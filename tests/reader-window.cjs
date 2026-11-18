const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
function scenario(resume,windowed=false){
    const events={},frames=[],pending=[],requests=[],storage=new Map();let active=0,max=0,y=0;
    if(resume!==undefined)storage.set('jingman.position:/chapter/test',JSON.stringify({page:resume,ratio:0}));
    class Element{
        constructor(tag){this.tag=tag;this.children=[];this.style={};this.naturalWidth=0;}
        appendChild(e){e.parent=this;this.children.push(e);}
        remove(){this.parent.children=this.parent.children.filter(e=>e!==this);}
        removeAttribute(){this._src=null;}
        set src(value){this._src=value;requests.push(value);active++;max=Math.max(max,active);pending.push(()=>{active--;this.naturalWidth=1080;if(this.onload)this.onload();});}
        get src(){return this._src;}
        getBoundingClientRect(){const height=this.style.height==='auto'?1000:parseFloat(this.style.height)||1000;let top=0;if(this.parent)for(const s of this.parent.children){if(s===this)break;top+=s.style.height==='auto'?1000:parseFloat(s.style.height)||1000;}return {top:top-y,bottom:top-y+height,height};}
    }
    const container=new Element('ul'),notice=new Element('div');
    const ctx={window:{readerImageUrl:x=>x},location:{pathname:'/chapter/test'},innerHeight:1000,get scrollY(){return y;},scrollTo:(x,n)=>{y=n;},document:{getElementById:id=>id==='images'?container:notice,createElement:tag=>new Element(tag),addEventListener(){}},addEventListener:(e,f)=>events[e]=f,requestAnimationFrame:f=>frames.push(f),setTimeout:()=>1,clearTimeout(){},localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),key:i=>[...storage.keys()][i],get length(){return storage.size;},removeItem:k=>storage.delete(k)},URL,console};
    vm.createContext(ctx);vm.runInContext(fs.readFileSync('app/src/main/assets/reader.js','utf8'),ctx);
    ctx.window.readerStart({images:Array.from({length:150},(_,i)=>'page-'+i),source_id:10,windowed});
    const flush=()=>{let n=0;while(frames.length){assert.ok(n++<100,'animation loop');frames.shift()();}};
    const settle=()=>{let n=0;flush();while(pending.length){assert.ok(n++<500,'unbounded preload');pending.shift()();flush();}};
    flush();assert.equal(pending.length,windowed?3:6,'parallel load limit');
    if(resume!==undefined)assert.equal(requests[0],'page-'+resume,'resume loads target first');
    settle();assert.ok(max<=(windowed?3:6));
    if(resume!==undefined)assert.equal(ctx.window.readerPastSecond(),true,'restored later page enters fullscreen');
    else {assert.equal(ctx.window.readerPastSecond(),false,'first page keeps controls');events.pointerdown();y=2100;assert.equal(ctx.window.readerPastSecond(),true,'passing the second page triggers fullscreen');y=0;}
    if(windowed){
        assert.ok(requests.length<=10,'long chapters must not eagerly load a half chapter');
        events.pointerdown();y=60000;events.scroll();settle();
        assert.ok(container.children.filter(p=>p.children[0].src).length<=16,'offscreen decoded images must be released');
        y=0;events.scroll();settle();
        assert.ok(container.children[0].children[0].src,'returning to the first page restores its image');
        assert.ok(max<=3,'scrolling must keep the concurrency bound');
        events.pagehide();assert.equal(container.children.filter(p=>p.children[0].src).length,0,'page close releases images');
        return;
    }
    assert.equal(requests.length,resume===undefined?75:150,'half chapter queued immediately; resumed second half is included');
    assert.equal(new Set(requests).size,requests.length,'no duplicate initial requests');
    events.pointerdown();y=60000;events.scroll();settle();
    assert.ok(container.children.filter(p=>p.children[0].src).length>=75,'loaded pages are retained while reading');
    y=container.children.slice(0,72).reduce((n,p)=>n+p.getBoundingClientRect().height,0);events.scroll();settle();
    assert.equal(requests.length,150,'second half queued near midpoint');
    assert.equal(new Set(requests).size,requests.length,'scrolling does not redownload images');
    events.pagehide();assert.equal(container.children.filter(p=>p.children[0].src).length,0,'page close releases images');
}
scenario();scenario(80);scenario(undefined,true);scenario(80,true);console.log('Chapter tests passed: original half-chapter policy; windowed long chapters, three-request bound, offscreen release, backward navigation and resume.');
