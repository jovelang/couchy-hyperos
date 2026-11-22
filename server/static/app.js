'use strict';
const paths={tv:'M3 5h18v13H3z M8 22h8 M12 18v4',screen:'M3 4h18v15H3z M7 22h10',remote:'M8 2h8v20H8z M10.5 6h3 M10.5 10h3 M12 15v3',apps:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',settings:'M4 6h16 M4 12h16 M4 18h16 M8 3v6 M16 9v6 M10 15v6',folder:'M3 6h7l2 2h9v12H3z M3 8V4h7l2 2h9v2',package:'M3 7l9-5 9 5v10l-9 5-9-5z M3 7l9 5 9-5 M12 12v10 M8 4l9 5',power:'M12 2v10 M6 5a9 9 0 1 0 12 0',up:'M6 15l6-6 6 6',down:'M6 9l6 6 6-6',left:'M15 6l-6 6 6 6',right:'M9 6l6 6-6 6','arrow-right':'M4 12h15 M14 6l6 6-6 6',back:'M9 5l-7 7 7 7 M3 12h13a5 5 0 0 1 0 10',home:'M3 10l9-7 9 7 M5 9v12h5v-7h4v7h5V9',menu:'M4 6h16 M4 12h16 M4 18h16',camera:'M3 6h5l2-3h4l2 3h5v15H3z M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0',download:'M12 3v12 M7 10l5 5 5-5 M4 16v5h16v-5',upload:'M12 16V4 M7 9l5-5 5 5 M4 16v5h16v-5',expand:'M9 3H3v6 M15 3h6v6 M3 15v6h6 M21 15v6h-6',refresh:'M20 8V3l-4 4 M4 16v5l4-4 M20 8a8 8 0 0 0-14-3 M4 16a8 8 0 0 0 14 3',input:'M12 4h9v16h-9 M3 12h12 M10 7l5 5-5 5',wifi:'M2 8a16 16 0 0 1 20 0 M5 12a11 11 0 0 1 14 0 M8.5 16a6 6 0 0 1 7 0 M12 20h.01',play:'M7 4l10 8-10 8z M21 5v14','volume-up':'M3 9h4l5-5v16l-5-5H3z M16 8a6 6 0 0 1 0 8 M19 4a11 11 0 0 1 0 16','volume-down':'M3 9h4l5-5v16l-5-5H3z M16 8a6 6 0 0 1 0 8',mute:'M3 9h4l5-5v16l-5-5H3z M17 9l5 6 M22 9l-5 6',close:'M5 5l14 14 M19 5L5 19',file:'M5 2h9l5 5v15H5z M14 2v6h5',check:'M4 12l5 5L20 6'};
const icon=n=>`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[n]||paths.tv}"/></svg>`;
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const e=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function icons(root=document){root.querySelectorAll('[data-icon]').forEach(x=>{x.innerHTML=icon(x.dataset.icon);x.removeAttribute('data-icon');});}
icons();
let authenticated=false,page='control',currentStatus={},autoShot=false,shotBusy=false,shotURL=null,shotTimer=null,toastTimer=null,refreshTimer=null;
let apps=[],desktop={},draft={},filePath='/sdcard/Download',apk=null,jobCache=[],jobPoll=null;
const title={control:['电视控制台','CONTROL'],apps:['你的电视应用','APPLICATIONS'],settings:['桌面设置','PREFERENCES'],files:['文件传输','FILES'],jobs:['安装与任务记录','ACTIVITY']};
function toast(message){$('#toast').textContent=message;$('#toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').hidden=true,4300);}
async function api(path,body,method){
 const options={credentials:'same-origin'};
 if(body!==undefined){options.method=method||'POST';options.headers={'Content-Type':'application/json'};options.body=JSON.stringify(body);}
 const response=await fetch('/api'+path,options);let data;
 try{data=await response.json();}catch(_){throw new Error('服务暂时不可用，请稍后重试');}
 if(response.status===401){showLogin();throw new Error(data.error||'请重新连接');}
 if(!response.ok)throw new Error(data.error||data.detail||'操作未完成');return data;
}
function showLogin(){authenticated=false;$('#app').hidden=true;$('#login').hidden=false;clearInterval(refreshTimer);clearInterval(jobPoll);$('#code').focus();}
async function enter(){authenticated=true;$('#login').hidden=true;$('#app').hidden=false;await updateStatus();showPage(location.hash.slice(1)||'control');clearInterval(refreshTimer);refreshTimer=setInterval(()=>{if(!document.hidden)updateStatus();},5000);clearInterval(jobPoll);jobPoll=setInterval(()=>{if(!document.hidden && (page==='jobs'||jobCache.some(j=>['queued','running'].includes(j.state))))loadJobs(false);},2000);}
$('#login-form').addEventListener('submit',async event=>{
 event.preventDefault();const button=$('#login-form button[type=submit]');button.disabled=true;$('#login-error').textContent='';
 try{await api('/login',{code:$('#code').value});$('#code').value='';await enter();}catch(error){$('#login-error').textContent=error.message;}finally{button.disabled=false;}
});
$('#logout').addEventListener('click',async()=>{try{await api('/logout',{});}finally{showLogin();}});
async function updateStatus(){
 try{
  currentStatus=await api('/status');const s=currentStatus;$('#state-label').textContent=s.label;$('#power-label').textContent=s.label;
  $('#state-dot').className='state-dot '+(s.state==='on'?'on':['standby','screen_off'].includes(s.state)?'off':'');
  $('#state-time').textContent=s.updated_at?new Date(s.updated_at*1000).toLocaleTimeString('zh-CN',{hour12:false}):'—';
  $('#power-detail').textContent=s.state==='on'?'电视正在运行':s.state==='screen_off'?'按键或遥控可重新亮屏':s.state==='standby'?'可通过米家远程开机':'正在检查电视与米家连接';
  $('#foreground-label').textContent=s.foreground?.label||'暂无前台应用';$('#foreground-package').textContent=s.foreground?.package||(['standby','offline'].includes(s.state)?'开机后会显示当前应用':'等待应用信息');
  $('#mijia-label').textContent=s.mijia?.available?'可用':s.mijia?.configured?'暂未连接':'未配置';$('#shot-badge').textContent=s.label;
 }catch(error){if(authenticated){$('#state-label').textContent='控制服务未连接';$('#state-dot').className='state-dot';}}
}
async function showPage(name){
 if(!title[name])name='control';page=name;location.hash=name;$$('.page').forEach(x=>x.hidden=x.id!=='page-'+name);$$('[data-page]').forEach(x=>x.classList.toggle('active',x.dataset.page===name));$('#page-title').textContent=title[name][0];$('#page-kicker').textContent=title[name][1];
 try{if(name==='apps')await loadApps();if(name==='settings')await loadSettings();if(name==='files')await loadFiles();if(name==='jobs')await loadJobs();}catch(error){toast(error.message);}
}
$$('[data-page]').forEach(x=>x.addEventListener('click',()=>showPage(x.dataset.page)));
window.addEventListener('hashchange',()=>{const name=location.hash.slice(1);if(name!==page && authenticated)showPage(name);});
async function takeShot(){
 if(shotBusy||!authenticated)return;shotBusy=true;$('#shot-loading').hidden=false;
 try{
  const response=await fetch('/api/screenshot?t='+Date.now(),{credentials:'same-origin'});
  if(!response.ok){const err=await response.json();throw new Error(err.error||'截图失败');}
  const blob=await response.blob(),old=shotURL;shotURL=URL.createObjectURL(blob);$('#shot').src=shotURL;$('#shot').hidden=false;$('#screen-placeholder').hidden=true;$('#shot-badge').hidden=false;
  $('#preview-image').src=shotURL;if(old)setTimeout(()=>URL.revokeObjectURL(old),1000);
  const captured=Number(response.headers.get('X-Captured-At'))*1000;$('#shot-time').textContent='截图于 '+new Date(captured||Date.now()).toLocaleTimeString('zh-CN',{hour12:false});
 }catch(error){if(!autoShot)toast(error.message);}finally{shotBusy=false;$('#shot-loading').hidden=true;}
}
$('#take-shot').addEventListener('click',takeShot);
$('#auto-shot').addEventListener('click',()=>{autoShot=!autoShot;$('#auto-shot').setAttribute('aria-checked',String(autoShot));clearInterval(shotTimer);if(autoShot){takeShot();shotTimer=setInterval(()=>{if(!document.hidden && page==='control' && currentStatus.state==='on')takeShot();},3000);}});
$('#fullscreen').addEventListener('click',()=>{if(!shotURL)return toast('先获取一张截图');$('#preview-dialog').showModal();});$('#close-preview').addEventListener('click',()=>$('#preview-dialog').close());
let afterInputTimer;
function afterInput(){clearTimeout(afterInputTimer);afterInputTimer=setTimeout(()=>{updateStatus();if(page==='control')takeShot();},650);}
async function sendKey(key,hold=false){try{await api('/key',{key,hold});afterInput();}catch(error){toast(error.message);}}
$$('[data-key]').forEach(x=>{
 let timer,held=false;
 x.addEventListener('pointerdown',()=>{held=false;if(x.dataset.key==='ok')timer=setTimeout(()=>{held=true;sendKey('ok',true);},600);});
 ['pointerup','pointercancel','pointerleave'].forEach(n=>x.addEventListener(n,()=>clearTimeout(timer)));
 x.addEventListener('click',()=>{if(!held)sendKey(x.dataset.key);});
});
window.addEventListener('keydown',event=>{
 if(!authenticated||page!=='control'||$('#preview-dialog').open||['INPUT','TEXTAREA','SELECT','BUTTON','A'].includes(document.activeElement?.tagName))return;
 const key={ArrowUp:'up',ArrowDown:'down',ArrowLeft:'left',ArrowRight:'right',Enter:'ok',Escape:'back',' ':'play_pause'}[event.key];
 if(key){event.preventDefault();if(!event.repeat)sendKey(key);}
});
$$('[data-shortcut]').forEach(x=>x.addEventListener('click',async()=>{try{await api('/shortcut',{action:x.dataset.shortcut});await showPage('control');afterInput();}catch(error){toast(error.message);}}));
async function power(on){try{const job=await api('/power',{on});toast(job.message);await loadJobs(false);watchJob(job.id);}catch(error){toast(error.message);}}
$('#power-on').addEventListener('click',()=>power(true));$('#power-off').addEventListener('click',()=>power(false));
async function watchJob(id,download=false){
 for(let i=0;i<320 && authenticated;i++){
  try{
   const job=await api('/jobs/'+id);if(['done','error'].includes(job.state)){toast(job.message);await loadJobs(false);updateStatus();if(job.state==='done'&&download&&job.result?.download)location.assign(job.result.download);if(page==='files')loadFiles();return job;}
  }catch(error){toast(error.message);return;}
  await new Promise(resolve=>setTimeout(resolve,1500));
 }
}
async function loadApps(){
 $('#app-list').innerHTML='<div class="empty">正在读取电视应用…</div>';
 apps=await api('/apps');renderApps();
}
function renderApps(){
 const query=$('#app-search').value.trim().toLowerCase();const values=apps.filter(a=>(a.label+' '+a.package).toLowerCase().includes(query)).sort((a,b)=>a.label.localeCompare(b.label,'zh-CN'));
 $('#app-list').innerHTML=values.length?values.map(a=>`<article class="app-tile"><div class="app-top"><div class="app-avatar"><img src="/api/apps/${encodeURIComponent(a.package)}/icon" alt="" loading="lazy"></div><div><div class="app-name">${e(a.label)}</div><div class="app-pkg">${e(a.package)}</div>${currentStatus.foreground?.package===a.package?'<span class="tag">正在运行</span>':''}</div></div><div class="app-actions"><button class="small" data-launch="${e(a.package)}">打开</button><button class="hide-action" data-visibility="${e(a.package)}">${a.hidden?'在桌面显示':'从桌面隐藏'}</button></div></article>`).join(''):'<div class="empty">没有匹配的应用</div>';
 $$('#app-list img').forEach(x=>x.addEventListener('error',()=>{x.parentNode.textContent='TV';}));
 $$('[data-launch]').forEach(x=>x.addEventListener('click',async()=>{try{await api('/apps/'+encodeURIComponent(x.dataset.launch)+'/launch',{});toast('已打开应用');await showPage('control');afterInput();}catch(error){toast(error.message);}}));
 $$('[data-visibility]').forEach(x=>x.addEventListener('click',async()=>{const app=apps.find(a=>a.package===x.dataset.visibility);try{await api('/apps/'+encodeURIComponent(app.package)+'/visibility',{hidden:!app.hidden});app.hidden=!app.hidden;renderApps();toast('桌面显示已更新');}catch(error){toast(error.message);}}));
}
$('#apps-refresh').addEventListener('click',()=>loadApps().catch(x=>toast(x.message)));$('#app-search').addEventListener('input',renderApps);
async function loadSettings(){
 const [groups,cfg]=await Promise.all([api('/config/schema'),api('/config')]);desktop=cfg;draft={};$('#settings-save').disabled=true;
 $('#settings-form').innerHTML=groups.map((g,i)=>`<details ${i===0?'open':''}><summary>${e(g.title)}</summary>${g.items.map(item=>{
  const value=cfg[item.key];let control;
  if(item.type==='bool')control=`<button class="switch" role="switch" aria-checked="${Boolean(value)}" aria-label="${e(item.title)}" data-setting="${e(item.key)}" data-type="bool"></button>`;
  else if(item.type==='text')control=`<input maxlength="48" data-setting="${e(item.key)}" value="${e(value)}" aria-label="${e(item.title)}">`;
  else control=`<select data-setting="${e(item.key)}" aria-label="${e(item.title)}">${item.options.map(o=>`<option value="${o.value}" ${o.value===value?'selected':''}>${e(o.label)}</option>`).join('')}</select>`;
  return `<div class="setting-row"><span>${e(item.title)}</span>${control}</div>`;
 }).join('')}</details>`).join('')+`<details><summary>信号源名称</summary>${['HDMI1','HDMI2','HDMI3','HDMI4','TV','AV','DTMB'].map(key=>`<div class="setting-row"><span>${key}</span><input maxlength="24" data-alias="${key}" placeholder="自定义前缀" value="${e(cfg.inputAliases?.[key]||'')}" aria-label="${key} 自定义前缀"></div>`).join('')}</details>`;
 $$('[data-setting]').forEach(x=>{
  if(x.dataset.type==='bool')x.addEventListener('click',()=>{const v=x.getAttribute('aria-checked')!=='true';x.setAttribute('aria-checked',String(v));changeSetting(x.dataset.setting,v);});
  else x.addEventListener('change',()=>changeSetting(x.dataset.setting,x.tagName==='SELECT'?Number(x.value):x.value));
 });
 $$('[data-alias]').forEach(x=>x.addEventListener('change',()=>{draft.inputAliases={...(draft.inputAliases||desktop.inputAliases||{}),[x.dataset.alias]:x.value.trim()};$('#settings-save').disabled=false;}));
}
function changeSetting(key,value){if(value===desktop[key])delete draft[key];else draft[key]=value;$('#settings-save').disabled=Object.keys(draft).length===0;}
$('#settings-reload').addEventListener('click',()=>loadSettings().catch(x=>toast(x.message)));
$('#settings-save').addEventListener('click',async()=>{const b=$('#settings-save');b.disabled=true;try{desktop=await api('/config',draft,'PATCH');draft={};toast('已保存到电视');}catch(error){b.disabled=false;toast(error.message);}});
function bytes(value){return value<1024?value+' B':value<1024**2?(value/1024).toFixed(1)+' KB':value<1024**3?(value/1024**2).toFixed(1)+' MB':(value/1024**3).toFixed(2)+' GB';}
async function loadFiles(path=filePath){
 filePath=path;$('#file-list').innerHTML='<div class="empty">正在读取文件…</div>';
 const data=await api('/files?path='+encodeURIComponent(path));filePath=data.path;
 const parts=filePath.split('/').filter(Boolean);$('#breadcrumbs').innerHTML=parts.map((part,i)=>`<button data-directory="/${e(parts.slice(0,i+1).join('/'))}">${i===0?'内部存储':e(part)}</button>${i<parts.length-1?'<span>/</span>':''}`).join('');
 $('#file-up').disabled=filePath==='/sdcard';
 $('#file-list').innerHTML=data.entries.length?data.entries.map(item=>`<div class="file-row"><div class="file-name">${icon(item.directory?'folder':'file')}${item.directory?`<button data-directory="${e(item.path)}">${e(item.name)}</button>`:`<span>${e(item.name)}</span>`}</div><span>${item.directory?'—':bytes(item.size)}</span><span>${new Date(item.modified).toLocaleDateString('zh-CN')}</span>${item.directory?'<span></span>':`<button data-download="${e(item.path)}" title="下载文件" aria-label="下载 ${e(item.name)}">${icon('download')}</button>`}</div>`).join(''):'<div class="empty">文件夹是空的，可上传文件到这里</div>';
 $$('[data-directory]').forEach(x=>x.addEventListener('click',()=>loadFiles(x.dataset.directory).catch(e=>toast(e.message))));
 $$('[data-download]').forEach(x=>x.addEventListener('click',async()=>{try{const job=await api('/files/download',{path:x.dataset.download});toast('正在从电视读取文件');loadJobs(false);watchJob(job.id,true);}catch(error){toast(error.message);}}));
 if(data.truncated)toast('目录超过 1000 项，请进入子文件夹查看');
}
$('#file-up').addEventListener('click',()=>loadFiles(filePath.split('/').slice(0,-1).join('/')||'/sdcard').catch(e=>toast(e.message)));
$('#files-refresh').addEventListener('click',()=>loadFiles().catch(e=>toast(e.message)));$('#file-upload').addEventListener('click',()=>$('#file-input').click());
function upload(endpoint,file,path){
 return new Promise((resolve,reject)=>{
  const form=new FormData();form.append('file',file);if(path)form.append('path',path);
  const xhr=new XMLHttpRequest();xhr.open('POST','/api'+endpoint);xhr.withCredentials=true;
  let last=0;xhr.upload.addEventListener('progress',event=>{if(event.lengthComputable && Date.now()-last>650){toast('正在上传 '+file.name+' · '+Math.round(event.loaded/event.total*100)+'%');last=Date.now();}});
  xhr.addEventListener('load',()=>{let data;try{data=JSON.parse(xhr.responseText);}catch(_){return reject(new Error('上传未完成，请检查文件大小和连接'));}if(xhr.status>=200&&xhr.status<300)resolve(data);else reject(new Error(data.error||'上传失败'));});
  xhr.addEventListener('error',()=>reject(new Error('网络连接中断，请重新上传')));xhr.send(form);
 });
}
async function uploadFiles(files){
 for(const file of files){try{const job=await upload('/files/upload',file,filePath);toast('正在传送 '+file.name);await loadJobs(false);await watchJob(job.id);}catch(error){toast(error.message);}}
 if(page==='files')loadFiles();
}
$('#file-input').addEventListener('change',event=>{uploadFiles([...event.target.files]);event.target.value='';});
['dragenter','dragover'].forEach(n=>$('#drop-zone').addEventListener(n,event=>{event.preventDefault();$('#drop-zone').classList.add('dragover');}));
['dragleave','drop'].forEach(n=>$('#drop-zone').addEventListener(n,event=>{event.preventDefault();$('#drop-zone').classList.remove('dragover');if(n==='drop')uploadFiles([...event.dataTransfer.files]);}));
$('#choose-apk').addEventListener('click',()=>$('#apk-input').click());$('#apk-input').addEventListener('change',event=>{apk=event.target.files[0]||null;$('#apk-name').textContent=apk?apk.name+' · '+bytes(apk.size):'还没有选择安装包';$('#install-apk').disabled=!apk;});
$('#install-apk').addEventListener('click',async()=>{if(!apk)return;const b=$('#install-apk');b.disabled=true;try{const job=await upload('/install',apk);toast('正在安装到电视');loadJobs();await watchJob(job.id);apk=null;$('#apk-input').value='';$('#apk-name').textContent='还没有选择安装包';}catch(error){toast(error.message);b.disabled=false;}});
async function loadJobs(display=true){
 jobCache=await api('/jobs');if(!display&&page!=='jobs')return;
 const states={queued:'等待中',running:'进行中',done:'已完成',error:'未完成'};
 $('#job-list').innerHTML=jobCache.length?jobCache.map(j=>`<article class="job">${icon(j.kind==='install'?'package':j.kind==='power'?'power':j.kind==='download'?'download':'upload')}<div class="job-info"><div class="job-name">${e(j.name)}</div><div class="job-message">${e(j.message)}${j.result?.version?' · '+e(j.result.version):''}</div>${j.result?.download?`<a href="${e(j.result.download)}">下载文件</a>`:''}</div><div class="job-time">${new Date(j.created_at*1000).toLocaleString('zh-CN',{hour12:false})}</div><span class="job-state ${e(j.state)}">${states[j.state]||j.state}</span></article>`).join(''):'<div class="empty">还没有任务。可在上方安装 APK，或传送一个文件。</div>';
}
$('#jobs-refresh').addEventListener('click',()=>loadJobs().catch(e=>toast(e.message)));
(async()=>{try{const result=await api('/session');if(result.authenticated)await enter();else showLogin();}catch(error){showLogin();$('#login-error').textContent=error.message;}})();
