// Queue a contiguous half chapter immediately; keep loaded images until the chapter closes.
window.readerStart = (params,decryptImage) => {
    if(!Array.isArray(params.images)||!params.images.length) throw Error('No images');
    const key='jingman.position:'+location.pathname;
    let position=null,touched=false,active=0,disposed=false,scheduled=false,saveTimer;
    try{position=JSON.parse(localStorage.getItem(key));}catch(_){}
    if(!position||!Number.isInteger(position.page)||position.page<0||position.page>=params.images.length||!Number.isFinite(position.ratio))position=null;
    const notice=document.getElementById('notice'),container=document.getElementById('images');
    const pages=params.images.map((value,index)=>{
        const li=document.createElement('li'),img=document.createElement('img');
        li.style.height=Math.round(innerHeight*0.7)+'px';img.alt='第 '+(index+1)+' 页';img.referrerPolicy='no-referrer';img.decoding='async';
        li.appendChild(img);container.appendChild(li);
        return {li,img,url:window.readerImageUrl(value),state:'idle',blob:null,height:0};
    });
    window.readerPastSecond=()=>pages.length>2 && ((position && !touched && position.page>=2) || (pages[0].loadedOnce && pages[1].loadedOnce && pages[1].li.getBoundingClientRect().bottom<=0));
    function focus(){if(position&&!touched)return position.page;const i=pages.findIndex(p=>p.li.getBoundingClientRect().bottom>0);return i<0?pages.length-1:i;}
    function align(){if(position&&!touched){const r=pages[position.page].li.getBoundingClientRect();scrollTo(0,scrollY+r.top+r.height*Math.max(0,Math.min(1,position.ratio)));}}
    function schedule(){if(scheduled||disposed)return;scheduled=true;requestAnimationFrame(()=>{scheduled=false;pump();});}
    const half=Math.ceil(pages.length/2);
    let batchEnd=position&&position.page>=half ? pages.length : half;
    let loadedCount=0;
    function pump(){
        if(disposed)return;align();const at=focus();
        if(at>=Math.max(1,half-Math.min(10,Math.ceil(half/4)) ))batchEnd=pages.length;
        const order=[at];
        if(params.windowed){
            for(let i=at+1;i<Math.min(pages.length,at+7);i++)order.push(i);
            for(let i=Math.max(0,at-2);i<at;i++)order.push(i);
            pages.forEach((p,i)=>{if((i<at-3||i>at+10)&&p.state==='loaded'){p.li.style.height=Math.max(1,p.height)+'px';p.img.removeAttribute('src');p.state='idle';}});
        }else{
            for(let i=at+1;i<batchEnd;i++)order.push(i);
            for(let i=0;i<at;i++)order.push(i);
        }
        for(const i of order){if(active>=(params.windowed?3:6))break;if(i>=0&&i<pages.length&&(params.windowed||i<batchEnd)&&pages[i].state==='idle')load(pages[i]);}
        notice.style.display='block';
        notice.textContent=params.windowed?'第 '+(at+1)+'/'+pages.length+' 页 · 按需加载，已读图片保存在本机':'本话已加载 '+loadedCount+'/'+pages.length+' 张 · '+(batchEnd<pages.length?'正在缓存前半话':'正在缓存本话');
    }
    function load(p){
        p.state='loading';active++;let done=false;
        function finish(ok){
            if(done||disposed)return;done=true;active--;p.img.onload=p.img.onerror=null;
            if(ok){p.state='loaded';p.li.style.height='auto';p.height=Math.max(1,p.li.getBoundingClientRect().height);if(!p.loadedOnce){p.loadedOnce=true;loadedCount++;}}
            else{p.state='error';p.img.removeAttribute('src');if(p.blob){URL.revokeObjectURL(p.blob);p.blob=null;}const b=document.createElement('button');b.className='retry';b.textContent='此页加载失败，点此重试';b.onclick=()=>{b.remove();p.state='idle';touched=true;schedule();};p.li.appendChild(b);}
            schedule();
        }
        p.img.onload=()=>finish(p.img.naturalWidth>0);p.img.onerror=()=>finish(false);
        if(params.source_id==12&&!/^https?:\/\//.test(p.url)){
            fetch('https://img1.baipiaoguai.org'+p.url,{referrerPolicy:'no-referrer',cache:'no-store'}).then(r=>{if(!r.ok)throw Error(r.status);return r.arrayBuffer();}).then(decryptImage).then(blob=>{if(disposed){URL.revokeObjectURL(blob);return;}p.blob=blob;p.img.src=blob;}).catch(()=>finish(false));
        }else p.img.src=p.url;
    }
    function save(){
        if(!touched)return;const page=focus(),r=pages[page].li.getBoundingClientRect();
        try{
            localStorage.setItem(key,JSON.stringify({page,ratio:Math.max(0,Math.min(1,-r.top/Math.max(1,r.height))),updated:Date.now()}));
            const keys=[];for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k.startsWith('jingman.position:'))keys.push(k);}
            keys.sort((a,b)=>(JSON.parse(localStorage.getItem(a)||'{}').updated||0)-(JSON.parse(localStorage.getItem(b)||'{}').updated||0));
            keys.slice(0,Math.max(0,keys.length-100)).forEach(k=>localStorage.removeItem(k));
        }catch(_){}
    }
    function touch(){touched=true;schedule();}
    addEventListener('touchstart',touch,{passive:true});addEventListener('pointerdown',touch,{passive:true});addEventListener('wheel',touch,{passive:true});
    addEventListener('scroll',()=>{schedule();clearTimeout(saveTimer);saveTimer=setTimeout(save,300);},{passive:true});
    addEventListener('resize',()=>{pages.filter(p=>p.state==='loaded').forEach(p=>p.height=p.li.getBoundingClientRect().height);schedule();});
    addEventListener('pagehide',()=>{save();disposed=true;pages.forEach(p=>{p.img.onload=p.img.onerror=null;p.img.removeAttribute('src');if(p.blob)URL.revokeObjectURL(p.blob);});});
    document.addEventListener('visibilitychange',save);schedule();
};
