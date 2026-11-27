const {createHmac,timingSafeEqual,randomBytes}=require('node:crypto');
const admins=new Set(['phantom.master1245@gmail.com','surakiatforwork@gmail.com','surakiat16082000@gmail.com']);
const validId=v=>typeof v==='string'&&/^[a-f0-9]{64}$/.test(v);
const clean=(v,n)=>typeof v==='string'?v.trim().slice(0,n):'';
class HttpError extends Error{constructor(status,message){super(message);this.status=status;}}
function token(id){const key=process.env.P2CX_DEVICE_SECRET;if(!key||key.length<32)throw Error('Missing device secret');return createHmac('sha256',key).update('p2cx-device:'+id).digest('hex');}
const bearer=req=>/^Bearer ([^\s]+)$/.exec(req.headers.authorization||'')?.[1]||'';
async function bridge(action,payload={},actor=''){
 const url=process.env.P2CX_SHEETS_URL,key=process.env.P2CX_SHEETS_SECRET;
 if(!/^https:\/\/script\.google\.com\/macros\/s\/[a-zA-Z0-9_-]+\/exec$/.test(url||'')||!key||key.length<32)throw Error('Missing bridge configuration');
 const body=JSON.stringify({action,payload,actor}).replace(/[\u007f-\uffff]/g,c=>String.fromCharCode(92,117)+c.charCodeAt(0).toString(16).padStart(4,'0')),timestamp=Date.now(),nonce=randomBytes(16).toString('hex');
 const signature=createHmac('sha256',key).update(timestamp+'.'+nonce+'.'+body).digest('hex');
 const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({timestamp,nonce,body,signature}),signal:AbortSignal.timeout(18000)});
 const result=await response.json();if(!response.ok||result?.ok!==true)throw Error('Backend unavailable');
 if(result.status>=400)throw new HttpError(result.status,result.error||'Request rejected');return result;
}
function endpoint(run){return async(req,res)=>{res.setHeader('Cache-Control','no-store');if(req.method!=='POST')return res.status(405).json({ok:false});try{
 const body=typeof req.body==='string'?JSON.parse(req.body):req.body;
 if(!body||typeof body!=='object'||Array.isArray(body)||JSON.stringify(body).length>8192)throw new HttpError(400,'Invalid request');
 return res.status(200).json({ok:true,...await run(req,body)});
 }catch(e){return res.status(e.status||503).json({ok:false,error:e.status?e.message:'Service temporarily unavailable'});}};}
const access=endpoint(async(req,body)=>{
 if(!validId(body.deviceId)||!['register','checkin'].includes(body.type))throw new HttpError(400,'Invalid device');
 const expected=token(body.deviceId);
 if(body.type==='checkin'){const supplied=bearer(req);if(!validId(supplied)||!timingSafeEqual(Buffer.from(expected),Buffer.from(supplied)))throw new HttpError(401,'Register again');}
 const ip=clean(req.headers['x-vercel-forwarded-for']||req.headers['x-forwarded-for'],120).split(',')[0]||'unknown';
 const rateKey=createHmac('sha256',process.env.P2CX_DEVICE_SECRET).update('rate:'+ip).digest('hex');
 const result=await bridge('access',{type:body.type,deviceId:body.deviceId,nickname:clean(body.nickname,80),model:clean(body.model,100),version:clean(body.version,40),usage:body.usage,rateKey});
 if(typeof result.allowed!=='boolean'||typeof result.enabled!=='boolean'||!['pending','approved','blocked'].includes(result.deviceStatus)||typeof result.nickname!=='string')throw Error('Invalid permission decision');
 return {...result,...(body.type==='register'?{token:expected}:{})};
});
async function adminIdentity(req){
 const supplied=bearer(req);if(!supplied||supplied.length>4096)throw new HttpError(401,'Sign in required');
 const {getApps,initializeApp}=require('firebase-admin/app');const {getAuth}=require('firebase-admin/auth');
 if(!getApps().length)initializeApp({projectId:JSON.parse(process.env.P2CX_FIREBASE_WEB_CONFIG).projectId});
 let identity;try{identity=await getAuth().verifyIdToken(supplied);}catch{throw new HttpError(401,'Sign in again');}
 const email=identity.email?.toLowerCase();if(!identity.email_verified||identity.firebase?.sign_in_provider!=='google.com'||!admins.has(email))throw new HttpError(403,'Admin required');return email;
}
const admin=endpoint(async(req,body)=>{
 const actor=await adminIdentity(req);if(!['getState','updateDevice','setGlobalEnabled'].includes(body.action))throw new HttpError(400,'Invalid action');
 return bridge(body.action,body.payload||{},actor);
});
function config(req,res){res.setHeader('Cache-Control','no-store');if(req.method!=='GET')return res.status(405).end();try{const {apiKey,authDomain,projectId,appId}=JSON.parse(process.env.P2CX_FIREBASE_WEB_CONFIG);if(![apiKey,authDomain,projectId,appId].every(v=>typeof v==='string'&&v))throw Error();return res.json({apiKey,authDomain,projectId,appId});}catch{return res.status(503).json({error:'Admin login is not configured'});}}
module.exports={access,admin,config};
