/** P2C_X control backend. Deploy as owner, access Anyone. Only signed Vercel requests are accepted. */
const SPREADSHEET_ID = '1ZbfSoXAKc5ky9kS8UFC-lCpL_p7Tyksqa6_T0GW5QDM';
const DEVICE_HEADERS = ['deviceId','nickname','status','notes','model','version','firstSeen','lastSeen','updatedAt','updatedBy'];
const ADMIN_EMAILS = ['phantom.master1245@gmail.com', 'surakiatforwork@gmail.com', 'surakiat16082000@gmail.com'];
const STATUSES = ['pending','approved','blocked'];

function book_() { return SpreadsheetApp.openById(SPREADSHEET_ID); }
function json_(data) { return ContentService.createTextOutput(JSON.stringify(data)).setMimeType(ContentService.MimeType.JSON); }
function text_(value, limit) { return typeof value === 'string' ? value.trim().slice(0, limit) : ''; }
function id_(value) {
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) throw Error('Invalid device ID');
  return value;
}
function sheetText_(value) {
  return /^[=+@-]/.test(value) ? "'" + value : value;
}
function lock_(run) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(4000)) throw Error('Busy; retry later');
  try { return run(); } finally { lock.releaseLock(); }
}
function table_(name, headers) {
  const sheet = book_().getSheetByName(name);
  if (!sheet) throw Error('Run setupDatabase first');
  if (sheet.getRange(1, 1, 1, headers.length).getValues()[0].some((v, i) => v !== headers[i])) {
    throw Error('Unexpected headers in ' + name);
  }
  return sheet;
}
function setupDatabase() {
  lock_(() => {
    const book = book_();
    for (const [name, headers] of [
      ['Devices', DEVICE_HEADERS], ['Settings', ['key', 'value']],
      ['AuditLogs', ['time', 'actor', 'action', 'target', 'value']],
      ['UsageDaily', ['day','deviceId','opens','confirmed','updatedAt']]
    ]) {
      let sheet = book.getSheetByName(name);
      if (!sheet) sheet = book.insertSheet(name);
      if (sheet.getLastRow() && sheet.getRange(1, 1, 1, headers.length).getValues()[0].some((v, i) => v !== headers[i])) {
        throw Error('Existing data has different headers: ' + name);
      }
      sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
      sheet.setFrozenRows(1);
    }
    const settings = table_('Settings', ['key', 'value']);
    if (settings.getLastRow() === 1) settings.appendRow(['enabled', 'true']);
    const props = PropertiesService.getScriptProperties();
    if (!props.getProperty('P2CX_BRIDGE_SECRET')) {
      props.setProperty('P2CX_BRIDGE_SECRET', Utilities.getUuid() + Utilities.getUuid());
    }
    SpreadsheetApp.flush();
  });
  console.log('Ready. Copy P2CX_BRIDGE_SECRET from Project settings > Script properties into Vercel.');
}
function doGet() { return json_({ ok: true, service: 'P2C_X Sheets', apiVersion: 3 }); }

function doPost(event) {
  try {
    const raw = event && event.postData && event.postData.contents;
    if (!raw || raw.length > 16000) throw Error('Invalid request');
    const envelope = JSON.parse(raw);
    const secret = typeof P2CX_DEPLOY_KEY === 'string' ? P2CX_DEPLOY_KEY : PropertiesService.getScriptProperties().getProperty('P2CX_BRIDGE_SECRET');
    if (!secret || !Number.isSafeInteger(envelope.timestamp)
        || Math.abs(Date.now() - envelope.timestamp) > 60000
        || !/^[a-f0-9]{32}$/.test(envelope.nonce) || typeof envelope.body !== 'string') {
      return json_({ ok: false, status: 401, error: 'Unauthorized bridge' });
    }
    const signed = envelope.timestamp + '.' + envelope.nonce + '.' + envelope.body;
    const signature = Utilities.computeHmacSha256Signature(signed, secret)
      .map(b => ('0' + ((b + 256) % 256).toString(16)).slice(-2)).join('');
    let difference = signature.length ^ String(envelope.signature).length;
    for (let i = 0; i < signature.length; i++) difference |= signature.charCodeAt(i) ^ String(envelope.signature).charCodeAt(i);
    if (difference) return json_({ ok: false, status: 401, error: 'Unauthorized bridge' });
    if (typeof P2CX_DEPLOY_KEY === 'string' && PropertiesService.getScriptProperties().getProperty('P2CX_BRIDGE_SECRET') !== secret) PropertiesService.getScriptProperties().setProperty('P2CX_BRIDGE_SECRET', secret);
    const request = JSON.parse(envelope.body);
    const result = lock_(() => {
      const cache = CacheService.getScriptCache();
      const replayKey = 'request:' + envelope.nonce;
      const prior = cache.get(replayKey);
      if (prior) return JSON.parse(prior);
      const result = handle_(request);
      SpreadsheetApp.flush();
      const encoded = JSON.stringify(result);
      if (encoded.length < 80000) cache.put(replayKey, encoded, 90);
      return result;
    });
    return json_({ ok: true, ...result });
  } catch (error) {
    // Infrastructure failures must never be interpreted as a device suspension.
    return json_({ ok: false, status: 503, error: 'Sheets backend unavailable' });
  }
}

function records_() {
 const sheet=table_('Devices',DEVICE_HEADERS),n=sheet.getLastRow()-1;
 const devices=(n?sheet.getRange(2,1,n,DEVICE_HEADERS.length).getValues():[]).map((r,i)=>{
  const d={row:i+2};DEVICE_HEADERS.forEach((k,j)=>d[k]=r[j]);
  for(const k of ['firstSeen','lastSeen','updatedAt'])d[k]=Number(d[k])||0;
  return d;
 }).filter(d=>/^[a-f0-9]{64}$/.test(String(d.deviceId)));
 if(new Set(devices.map(d=>d.deviceId)).size!==devices.length)throw Error('Duplicate IDs');
 return {sheet,devices};
}
function global_(){
 const cache=CacheService.getScriptCache(),old=cache.get('globalEnabled');if(old==='true'||old==='false')return old==='true';
 const rows=table_('Settings',['key','value']).getDataRange().getValues();const row=rows.find(r=>r[0]==='enabled');
 if(!row||![true,false,'true','false'].includes(row[1]))throw Error('Invalid settings');
 const enabled=row[1]===true||row[1]==='true';cache.put('globalEnabled',String(enabled),300);return enabled;
}
function nickname_(value){
 const name=typeof value==='string'?value.trim().normalize('NFC'):'';
 if(!name||[...name].length>40||/[\u0000-\u001f\u007f]/.test(name))throw Error('Nickname must contain 1-40 visible characters');return name;
}
function writeDevice_(sheet,d){sheet.getRange(d.row,1,1,DEVICE_HEADERS.length).setValues([DEVICE_HEADERS.map(k=>typeof d[k]==='string'?sheetText_(d[k]):d[k]??'')]);}
function decision_(d){
 if(!STATUSES.includes(d.status))throw Error('Invalid device status');
 const enabled=global_();return {enabled,allowed:enabled&&d.status==='approved',deviceStatus:d.status,nickname:String(d.nickname)};
}
function usage_(id,usage){
 if(!Array.isArray(usage)||!usage.length)return;
 if(usage.length>7)throw Error('Too many usage days');
 const table=table_('UsageDaily',['day','deviceId','opens','confirmed','updatedAt']);
 const rows=table.getDataRange().getValues();
 for(const item of usage){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(item.day)||!Number.isSafeInteger(item.opens)||!Number.isSafeInteger(item.confirmed)||item.opens<0||item.confirmed<0||item.opens>1000000||item.confirmed>1000000)throw Error('Invalid usage');
  const when=Date.parse(item.day+'T00:00:00Z');if(!Number.isFinite(when)||Math.abs(Date.now()-when)>8*86400000)throw Error('Invalid usage date');
  let index=rows.findIndex(r=>r[0]===item.day&&r[1]===id);
  if(index<0){index=rows.length;rows.push([item.day,id,0,0,0]);}
  const old=rows[index];if(old[2]>=item.opens&&old[3]>=item.confirmed)continue;
  const row=[item.day,id,Math.max(Number(old[2])||0,item.opens),Math.max(Number(old[3])||0,item.confirmed),Date.now()];
  table.getRange(index+1,1,1,5).setValues([row]);rows[index]=row;
 }
}
function handle_(request){
 const action=request.action,p=request.payload||{},cache=CacheService.getScriptCache(),now=Date.now();
 if(action==='access'){
  id_(p.deviceId);if(!['register','checkin'].includes(p.type))throw Error('Invalid request');
  const cached=cache.get('device:'+p.deviceId);
  if(cached){const d=JSON.parse(cached);if(d.status!=='pending'&&d.model===text_(p.model,100)&&d.version===text_(p.version,40)){
   usage_(p.deviceId,p.usage);return decision_(d);
  }}
 }
 const {sheet,devices}=records_();
 if(action==='access'){
  let d=devices.find(d=>d.deviceId===p.deviceId);
  if(!d&&p.type==='checkin')return {status:401,error:'Register again'};
  if(!d){
   const name=nickname_(p.nickname);
   if(!/^[a-f0-9]{64}$/.test(p.rateKey))throw Error('Missing rate key');
   const key='registration:'+p.rateKey+':'+Math.floor(now/3600000);const count=Number(cache.get(key))||0;
   if(count>=30)return {status:429,error:'Please retry later'};cache.put(key,String(count+1),3600);
   d={deviceId:p.deviceId,nickname:name,status:'approved',notes:'',firstSeen:now,lastSeen:0,updatedAt:now,updatedBy:'AUTO',row:sheet.getLastRow()+1};
   devices.push(d);
   table_('AuditLogs',['time','actor','action','target','value']).appendRow([now,'AUTO','autoApprove',d.deviceId,'new registration']);
  }
  // Approve previous waiting registrations too; never reset an admin block.
  if(d.status==='pending'){
   d.status='approved';d.updatedAt=now;d.updatedBy='AUTO';writeDevice_(sheet,d);
   table_('AuditLogs',['time','actor','action','target','value']).appendRow([now,'AUTO','autoApprove',d.deviceId,'pending registration']);
  }
  // Existing nicknames are immutable from the app, including repeated registration.
  if(!d.lastSeen||now-d.lastSeen>=900000||d.version!==text_(p.version,40)||d.model!==text_(p.model,100)){
   d.model=text_(p.model,100);d.version=text_(p.version,40);d.lastSeen=now;writeDevice_(sheet,d);
  }
  cache.put('device:'+p.deviceId,JSON.stringify(d),300);usage_(p.deviceId,p.usage);return decision_(d);
 }
 if(!ADMIN_EMAILS.includes(request.actor))return {status:403,error:'Admin required'};
 const log=table_('AuditLogs',['time','actor','action','target','value']);
 if(action==='updateDevice'){
  const d=devices.find(d=>d.deviceId===id_(p.deviceId));if(!d)return {status:404,error:'Device not found'};
  if(!STATUSES.includes(p.status))throw Error('Invalid status');
  const name=nickname_(p.nickname);Object.assign(d,{nickname:name,status:p.status,notes:text_(p.notes,500),updatedAt:now,updatedBy:request.actor});
  writeDevice_(sheet,d);cache.remove('device:'+d.deviceId);log.appendRow([now,request.actor,action,d.deviceId,sheetText_(JSON.stringify({nickname:name,status:d.status}))]);
 }else if(action==='setGlobalEnabled'){
  if(typeof p.enabled!=='boolean')throw Error('Invalid status');
  const settings=table_('Settings',['key','value']),row=settings.getDataRange().getValues().findIndex(r=>r[0]==='enabled')+1;
  settings.getRange(row,2).setValue(String(p.enabled));cache.put('globalEnabled',String(p.enabled),300);log.appendRow([now,request.actor,action,'ALL',String(p.enabled)]);
 }else if(action!=='getState')throw Error('Invalid action');
 const selected=devices.slice().sort((a,b)=>b.lastSeen-a.lastSeen);
 const search=text_(p.search,80).toLowerCase();const filtered=search?selected.filter(d=>[d.deviceId,d.nickname,d.model,d.notes].some(v=>String(v).toLowerCase().includes(search))):selected;
 const size=Math.min(30,Math.max(0,log.getLastRow()-1));const audit=size?log.getRange(log.getLastRow()-size+1,1,size,5).getValues().reverse().map(r=>({time:r[0],actor:r[1],action:r[2],target:r[3],value:r[4]})):[];
 const day=new Date(now+7*3600000).toISOString().slice(0,10);
 const usageRows=table_('UsageDaily',['day','deviceId','opens','confirmed','updatedAt']).getDataRange().getValues().slice(1).filter(r=>r[0]===day);
 const usage=new Map(usageRows.map(r=>[r[1],{opensToday:Number(r[2])||0,confirmedToday:Number(r[3])||0}]));
 return {state:{enabled:global_(),counts:{opensToday:usageRows.reduce((n,r)=>n+(Number(r[2])||0),0),confirmedToday:usageRows.reduce((n,r)=>n+(Number(r[3])||0),0),total:devices.length,pending:devices.filter(d=>d.status==='pending').length,approved:devices.filter(d=>d.status==='approved').length,blocked:devices.filter(d=>d.status==='blocked').length,active:devices.filter(d=>d.lastSeen>=now-86400000).length},devices:filtered.slice(0,200).map(({row,...d})=>({...d,...(usage.get(d.deviceId)||{opensToday:0,confirmedToday:0})})),audit}};
}
