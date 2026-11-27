const vm = require('node:vm');
const fs = require('node:fs');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const tables = new Map(), properties = new Map(), cache = new Map();
let exported;
function sheet(name) {
  const rows = [];
  const api = {
    getLastRow: () => rows.length,
    setFrozenRows() {},
    appendRow(row) { rows.push([...row]); },
    getDataRange: () => ({ getValues: () => rows.map(row => [...row]) }),
    getRange(r, c, height = 1, width = 1) {
      return {
        getValues: () => Array.from({ length: height }, (_, i) =>
          Array.from({ length: width }, (_, j) => rows[r - 1 + i]?.[c - 1 + j] ?? '')),
        setValues(values) { values.forEach((row, i) => {
          const target = rows[r - 1 + i] ||= [];
          row.forEach((value, j) => target[c - 1 + j] = value);
        }); },
        setValue(value) { (rows[r - 1] ||= [])[c - 1] = value; }
      };
    }
  };
  tables.set(name, api);
  return api;
}
const book = { getSheetByName: name => tables.get(name), insertSheet: sheet };
const runtime = vm.createContext({
  console: { log() {} }, Date,
  SpreadsheetApp: { openById: () => book, flush() {} },
  LockService: { getScriptLock: () => ({ tryLock: () => true, releaseLock() {} }) },
  CacheService: { getScriptCache: () => ({
    get: key => cache.get(key) ?? null, put: (key, value) => cache.set(key, value), remove: key => cache.delete(key)
  }) },
  PropertiesService: { getScriptProperties: () => ({
    getProperty: key => properties.get(key), setProperty: (key, value) => properties.set(key, value)
  }) },
  Utilities: {
    getUuid: () => crypto.randomUUID(),
    computeHmacSha256Signature: (message, secret) => [...crypto.createHmac('sha256', secret).update(message).digest()]
  },
  ContentService: { MimeType: { JSON: 'json' }, createTextOutput: text => ({ setMimeType: () => JSON.parse(text) }) },
  DriveApp: { getFileById: () => ({ getBlob: () => ({ getDataAsString: () => JSON.stringify(exported) }) }) }
});
vm.runInContext(fs.readFileSync('control-center/apps-script/Code.gs', 'utf8'), runtime);
runtime.setupDatabase();

const id='a'.repeat(64),other='b'.repeat(64),rateKey='d'.repeat(64),admin='surakiatforwork@gmail.com';
function request(action,payload={},actor=''){
 const body=JSON.stringify({action,payload,actor}),timestamp=Date.now(),nonce=crypto.randomBytes(16).toString('hex');
 const signature=crypto.createHmac('sha256',properties.get('P2CX_BRIDGE_SECRET')).update(timestamp+'.'+nonce+'.'+body).digest('hex');
 return runtime.doPost({postData:{contents:JSON.stringify({body,timestamp,nonce,signature})}});
}
let first=request('access',{type:'register',deviceId:id,nickname:'Tester',rateKey});
assert.equal(first.ok,true);assert.equal(first.allowed,true);assert.equal(first.deviceStatus,'approved');
assert.equal(request('updateDevice',{deviceId:id,nickname:'Tester',status:'pending',notes:'Keep note'},admin).ok,true);
assert.equal(request('access',{type:'checkin',deviceId:id}).deviceStatus,'approved','existing pending automatically approved');
assert.equal(request('getState',{},admin).state.devices[0].notes,'Keep note','automatic approval preserves notes');
// A cached pending entry must not retain the previous manual approval policy.
const pending=JSON.parse(cache.get('device:'+id));pending.status='pending';cache.set('device:'+id,JSON.stringify(pending));
assert.equal(request('access',{type:'checkin',deviceId:id}).allowed,true,'pending cache is refreshed');
assert.equal(request('access',{type:'register',deviceId:id,nickname:'Changed',rateKey}).nickname,'Tester','app cannot rename');
assert.equal(request('updateDevice',{deviceId:id,nickname:'Admin edit',status:'approved',notes:''},admin).ok,true);
assert.equal(request('access',{type:'checkin',deviceId:id}).allowed,true);
assert.equal(request('access',{type:'register',deviceId:id,nickname:'Try overwrite',rateKey}).nickname,'Admin edit');
assert.equal(request('updateDevice',{deviceId:id,nickname:'No',status:'blocked'},'outsider@example.com').status,403);
assert.equal(request('setGlobalEnabled',{enabled:false},admin).state.enabled,false);
assert.equal(request('access',{type:'checkin',deviceId:id}).allowed,false);
const second=request('access',{type:'register',deviceId:other,nickname:'While paused',rateKey});
assert.equal(second.deviceStatus,'approved');assert.equal(second.allowed,false,'global pause still overrides automatic approval');
request('setGlobalEnabled',{enabled:true},admin);
request('updateDevice',{deviceId:id,nickname:'Admin edit',status:'blocked',notes:''},admin);
assert.equal(request('access',{type:'register',deviceId:id,nickname:'Reinstall',rateKey}).deviceStatus,'blocked','reregister does not reset block');
assert.equal(request('access',{type:'checkin',deviceId:'c'.repeat(64)}).status,401);
assert.equal(runtime.doPost({postData:{contents:'{}'}}).status,401);
const day=new Date().toISOString().slice(0,10),usage=[{day,opens:3,confirmed:2}];
request('access',{type:'checkin',deviceId:id,usage});request('access',{type:'checkin',deviceId:id,usage});
assert.equal(tables.get('UsageDaily').getLastRow(),2,'usage retry is idempotent');
assert.equal(tables.get('UsageDaily').getRange(2,4).getValues()[0][0],2);
assert.equal(request('getState',{},admin).state.devices.find(d=>d.deviceId===id).nickname,'Admin edit');
console.log('PASS: automatic approval, pending migration/cache, preserved notes, immutable app name, admin rename, block persistence, global switch, auth, usage deduplication');
