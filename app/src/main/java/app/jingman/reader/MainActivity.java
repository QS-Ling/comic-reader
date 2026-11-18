package app.jingman.reader;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    static final String ORIGIN = "https://www.qimanwu.app";
    static final String DEFAULT = ORIGIN + "/comic/Zr2gL5nd86";
    final ExecutorService worker = Executors.newFixedThreadPool(2);
    final CoverLoader covers=new CoverLoader();
    final ArrayList<String> paths = new ArrayList<>(), names = new ArrayList<>();
    QimanChapters qimanChapters;
    String readingPath="";
    LinearLayout root, bar, readerTop; TextView heading, status; WebView web;
    boolean readerControlsVisible=true, readerAutoHidden=false; float readerTouchX,readerTouchY; boolean readerMoved; long readerTouchTime;
    ReaderUi ui;FrameLayout contentHost;boolean mineView;
    android.content.SharedPreferences prefs;
    LibraryStore library;
    ChapterCountLoader chapterCounts;
    CombinedSearchResults combinedSearch;
    GuestPageClient guestPages;
    QimanSource.PageReader qimanGuestReader;
    boolean shelving,returnToLibrary,historyView;
    String activeSource="baozi",bookCover="",bookSummary="";
    int browseGridPage;
    String book, title = "静漫"; int chapter = -1; volatile int generation = 0; boolean dark; volatile boolean destroyed;
    boolean browsing;
    String browseUrl=ORIGIN+"/";
    final ArrayDeque<String> siteHistory=new ArrayDeque<>();
    final LinkedHashMap<String,String> knownBooks=new LinkedHashMap<>();
    QimanSearchIndex qimanSearchIndex;
    volatile boolean qimanUpdating;
    final ArrayList<SiteIndex.Entry> siteFilters=new ArrayList<>();
    static final HomeRefreshPolicy QIMAN_HOME_REFRESH=new HomeRefreshPolicy();
    PullRefreshFrame homeRefreshFrame;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("reader", MODE_PRIVATE);
        chapterCounts=new ChapterCountLoader(this,MainActivity::fetchPassive);
        library=new LibraryStore(prefs);activeSource=prefs.getString("source","baozi");
        guestPages=new GuestPageClient(this);qimanGuestReader=guestPages::read;QimanSource.guestReader=qimanGuestReader;QimanSource.visitorCookies=GuestPageClient::clearanceCookies;QimanSource.visitorUserAgent=QimanSource.browserUserAgent(WebSettings.getDefaultUserAgent(this));BaoziSource.guestReader=guestPages;BaoziSource.visitorCookies=GuestPageClient::clearanceCookies;BaoziSource.visitorUserAgent=WebSettings.getDefaultUserAgent(this);
        try { ImageLoader.chapters=new ChapterCache(new File(getFilesDir(),"chapters")); ImageLoader.chapters.setLimit(prefs.getLong("chapterCacheLimit",0)); }
        catch(IOException e) { Toast.makeText(this,"章节缓存不可用，仍可在线阅读",Toast.LENGTH_LONG).show(); }
        if(prefs.getInt("cachePolicy",0)<3) {
            WebView cleaner=new WebView(this); cleaner.clearCache(true); cleaner.destroy();
            worker.execute(this::clearDiskCache);
            prefs.edit().putInt("cachePolicy",3).apply();
        }
        dark = prefs.getBoolean("dark", true);
        book = prefs.getString("book", DEFAULT);
        try { org.json.JSONObject saved=new org.json.JSONObject(prefs.getString("knownBooks","{}"));
            java.util.Iterator<String> keys=saved.keys();while(keys.hasNext()){String key=keys.next();String url=Sources.normalize(key);if(Sources.isComic(url))knownBooks.put(url,saved.getString(key));}
        } catch(Exception ignored) {}
        if(!openIntent(getIntent()))browse(home(),false,QIMAN_HOME_REFRESH.claimStartup());
        checkUpdates(false);
    }
    boolean openIntent(Intent intent){
        if(intent==null||!Intent.ACTION_VIEW.equals(intent.getAction())||intent.getData()==null)return false;
        try{openSiteLink(intent.getData().toString());return true;}catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return false;}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openIntent(intent);}
    int bg() { return Color.parseColor(dark ? "#101116" : "#FAF9FD"); }
    int fg() { return Color.parseColor(dark ? "#F2F1F7" : "#21202A"); }
    int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n); }
    TextView label(String s, int size) {
        TextView t = ui.text(s,size,fg(),false);
        t.setPadding(dp(16),dp(12),dp(16),dp(12)); return t;
    }
    void button(LinearLayout parent, String s, Runnable action) {
        TextView b=ui.action(s,false,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(4),dp(3),dp(4),dp(3));parent.addView(b,lp);
    }
    void baseScreen() {
        getWindow().getDecorView().setSystemUiVisibility(0);
        covers.cancelRequests();
        if(chapterCounts!=null)chapterCounts.cancelRequests();combinedSearch=null;homeRefreshFrame=null;
        if(web != null) { web.stopLoading(); web.clearCache(true); web.destroy(); web = null; }
        ui=new ReaderUi(this,dark);contentHost=null;
        root = new LinearLayout(this); root.setOrientation(1); root.setBackgroundColor(bg());
        setContentView(root); getWindow().setStatusBarColor(bg()); getWindow().setNavigationBarColor(ui.surface);
        int flags=getWindow().getDecorView().getSystemUiVisibility();flags=dark?flags&~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(Build.VERSION.SDK_INT>=26)flags=dark?flags&~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:flags|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    void frame(String subtitle) {
        baseScreen();LinearLayout top=ui.row();top.setPadding(dp(8),0,dp(8),0);
        top.addView(ui.iconButton("back","返回",this::onBackPressed),new LinearLayout.LayoutParams(dp(42),dp(48)));
        heading=ui.text(subtitle,17,ui.text,true);heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);top.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));heading.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(ui.iconButton("more","更多",this::screenMenu),new LinearLayout.LayoutParams(dp(42),dp(48)));root.addView(top);readerTop=top;
        status=ui.text("",11,ui.muted,false);status.setPadding(dp(20),dp(2),dp(20),dp(8));status.setMaxLines(2);root.addView(status);
    }

    void catalog() {
        save();browsing=false;shelving=false;mineView=false;chapter=-1;final int token=++generation;final String catalogBook=book;frame("漫画详情");status.setText("正在加载简介和目录…");
        final FrameLayout body=new FrameLayout(this);contentHost=body;root.addView(body,new LinearLayout.LayoutParams(-1,0,1));showLoading(body);
        worker.execute(()->{try{
            String html=fetch(catalogBook);ArrayList<String> pp=new ArrayList<>(),nn=new ArrayList<>();String parsedName,parsedSummary;
            final QimanChapters parsedChapters=Sources.baozi(catalogBook)?null:QimanChapters.parse(html);
            if(Sources.baozi(catalogBook)){BaoziSource.Detail detail=BaoziSource.detail(html,catalogBook);pp.addAll(detail.paths);nn.addAll(detail.names);parsedName=detail.title;parsedSummary=detail.author+"\n\n"+detail.summary;}
            else{LinkedHashMap<String,String> chapters=parsedChapters.chapters;pp.addAll(chapters.keySet());nn.addAll(chapters.values());Matcher tm=Pattern.compile("<title>(.*?)</title>").matcher(html);parsedName=tm.find()?plain(tm.group(1)).split("_")[0]:"漫画";parsedSummary=description(html);}
            if(pp.isEmpty())throw new IOException("未找到公开章节");final String name=parsedName,summary=parsedSummary,cover=Sources.detailCover(html,catalogBook);
            runOnUiThread(()->{if(destroyed||token!=generation)return;paths.clear();paths.addAll(pp);names.clear();names.addAll(nn);title=name;bookCover=cover;bookSummary=summary;library.remember(book,title,cover,summary);knownBooks.put(book,title);prefs.edit().putString("knownBooks",new org.json.JSONObject(knownBooks).toString()).apply();heading.setText(title);status.setText(Sources.name(book)+"  ·  共 "+paths.size()+" 话");
                qimanChapters=parsedChapters;
                chapterCounts.remember(book,paths.size());String last=prefs.getString(book+"/chapter","");final int lastIndex=paths.indexOf(parsedChapters==null?last:parsedChapters.canonical(last)),next=lastIndex<0?0:lastIndex;
                ListView list=new ListView(this);list.setDivider(new android.graphics.drawable.ColorDrawable(ui.line));list.setDividerHeight(Math.max(1,dp(1)));list.setSelector(new android.graphics.drawable.ColorDrawable(ui.tint));list.setPadding(dp(20),0,dp(20),0);list.setClipToPadding(false);
                LinearLayout header=new LinearLayout(this);header.setOrientation(1);header.setPadding(0,dp(10),0,dp(6));
                LinearLayout preview=ui.row();ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(ui.shape(ui.soft,10));image.setClipToOutline(true);preview.addView(image,new LinearLayout.LayoutParams(dp(106),dp(143)));
                LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(dp(16),0,0,0);TextView full=ui.text(title,19,ui.text,true);full.setMaxLines(3);full.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(full);TextView tag=ui.text(Sources.name(book),11,ui.accent,true);tag.setPadding(dp(8),dp(5),dp(8),dp(5));tag.setBackground(ui.shape(ui.tint,7));LinearLayout.LayoutParams tagLp=new LinearLayout.LayoutParams(-2,-2);tagLp.topMargin=dp(12);info.addView(tag,tagLp);TextView progress=ui.text(lastIndex<0?"从第一话开始新的故事":"上次读到："+names.get(lastIndex),11,ui.muted,false);progress.setMaxLines(2);progress.setPadding(0,dp(12),0,0);info.addView(progress);preview.addView(info,new LinearLayout.LayoutParams(0,-2,1));header.addView(preview);TextView coverState=coverCaption();header.addView(coverState);covers.bind(image,cover,book,coverState);
                TextView intro=ui.text(summary.isEmpty()?"这部漫画还没有简介。":summary,12,ui.muted,false);intro.setMaxLines(3);intro.setEllipsize(android.text.TextUtils.TruncateAt.END);intro.setLineSpacing(dp(4),1);intro.setPadding(0,dp(14),0,dp(20));intro.setOnClickListener(v->dialog().setTitle(title).setMessage(summary).setPositiveButton("关闭",null).show());header.addView(intro);
                image.setOnClickListener(v->{if(image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable){ImageView large=new ImageView(this);large.setScaleType(ImageView.ScaleType.FIT_CENTER);large.setImageDrawable(image.getDrawable());FrameLayout content=new FrameLayout(this);content.addView(large,new FrameLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.6)));dialog().setView(content).setPositiveButton("关闭",null).show();}else covers.retry(image,cover,book,coverState);});
                final boolean[] descending={prefs.getBoolean("chaptersDescending",false)};
                LinearLayout chapterHeader=ui.row();TextView chapters=ui.text("章节目录",16,ui.text,true);chapterHeader.addView(chapters,new LinearLayout.LayoutParams(0,dp(48),1));chapters.setGravity(Gravity.CENTER_VERTICAL);
                TextView sort=ui.chip(descending[0]?"逆序 ↓":"正序 ↑",false,()->{});chapterHeader.addView(sort,new LinearLayout.LayoutParams(-2,dp(36)));header.addView(chapterHeader);list.addHeaderView(header,null,false);
                ArrayAdapter<String> chapterAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,names){@Override public String getItem(int p){return names.get(ChapterOrder.index(p,names.size(),descending[0]));}@Override public View getView(int position,View old,ViewGroup parent){int p=ChapterOrder.index(position,names.size(),descending[0]);TextView row=old instanceof TextView?(TextView)old:ui.text("",13,ui.text,false);row.setText(names.get(p));row.setTextColor(p==lastIndex?ui.accent:ui.text);row.setBackgroundColor(p==lastIndex?ui.tint:Color.TRANSPARENT);row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setLayoutParams(new AbsListView.LayoutParams(-1,dp(49)));return row;}};
                list.setAdapter(chapterAdapter);sort.setOnClickListener(v->{descending[0]=!descending[0];prefs.edit().putBoolean("chaptersDescending",descending[0]).apply();sort.setText(descending[0]?"逆序 ↓":"正序 ↑");chapterAdapter.notifyDataSetChanged();list.setSelection(0);});
                list.setOnItemClickListener((a,v,p,id)->{int selected=ChapterOrder.index(p-list.getHeaderViewsCount(),paths.size(),descending[0]);if(selected>=0)read(selected);});body.removeAllViews();body.addView(list,new FrameLayout.LayoutParams(-1,-1));
                LinearLayout actions=ui.row();actions.setPadding(dp(16),dp(8),dp(16),dp(8));actions.setBackgroundColor(ui.surface);TextView favorite=ui.action(library.favorite(book)?"已收藏":"加入书架",false,()->{});favorite.setOnClickListener(v->{library.toggle(book);favorite.setText(library.favorite(book)?"已收藏":"加入书架");});actions.addView(favorite,new LinearLayout.LayoutParams(dp(104),dp(44)));TextView begin=ui.action(lastIndex<0?"开始阅读":"继续阅读",true,()->read(next));LinearLayout.LayoutParams beginLp=new LinearLayout.LayoutParams(0,dp(44),1);beginLp.leftMargin=dp(10);actions.addView(begin,beginLp);root.addView(actions);
            });
        }catch(Exception e){fail(token,"目录加载失败："+e.getMessage());}});
    }
    void changeBook() {
        EditText input = new EditText(this); input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI); input.setText(browsing?browseUrl:book);
        dialog().setTitle("包子漫画 / 奇漫屋链接").setView(input)
            .setPositiveButton("打开",(d,w) -> {
                try {
                    openSiteLink(input.getText().toString());
                } catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
            }).setNegativeButton("取消",null).show();
    }
    void openSiteLink(String address) {
        String url=Sources.normalize(address.startsWith("/")?URI.create(home()).resolve(address).toString():address);
        if(Sources.isComic(url)) {
            save();book=url.replaceAll("/$","");prefs.edit().putString("book",book).apply();catalog();
        } else browse(url,true);
    }

    void browse(String address,boolean remember) {
        browse(address,remember,false);
    }
    void browse(String address,boolean remember,boolean refreshHome) {
        final String url=Sources.normalize(address);
        activeSource=Sources.baozi(url)?"baozi":"qimanwu";prefs.edit().putString("source",activeSource).apply();save();
        if(remember&&browsing&&!url.equals(browseUrl)){if(siteHistory.size()>=30)siteHistory.removeFirst();siteHistory.addLast(browseUrl);}
        if(!url.equals(browseUrl))browseGridPage=0;
        browseUrl=url;browsing=true;shelving=false;mineView=false;returnToLibrary=false;chapter=-1;final int token=++generation;
        baseScreen();siteFilters.clear();
        LinearLayout brand=ui.row();brand.setPadding(dp(20),dp(4),dp(12),0);
        ReaderUi.Icon mark=new ReaderUi.Icon(this,"book",ui.accent);brand.addView(mark,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView wordmark=ui.text("静漫",22,ui.text,true);wordmark.setPadding(dp(9),0,0,0);brand.addView(wordmark,new LinearLayout.LayoutParams(0,dp(48),1));wordmark.setGravity(Gravity.CENTER_VERTICAL);
        TextView source=ui.chip(Sources.name(url)+"  ⌄",false,this::chooseSource);source.setBackground(ui.touch(ui.soft,16));brand.addView(source,new LinearLayout.LayoutParams(-2,dp(30)));
        brand.addView(ui.iconButton("more","更多阅读工具",this::screenMenu),new LinearLayout.LayoutParams(dp(42),dp(44)));root.addView(brand);
        LinearLayout search=ui.row();search.setPadding(dp(13),0,dp(5),0);search.setBackground(ui.shape(ui.soft,14));
        search.addView(new ReaderUi.Icon(this,"search",ui.muted),new LinearLayout.LayoutParams(dp(18),dp(18)));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(ui.text);input.setHintTextColor(ui.muted);input.setTextSize(13);input.setTypeface(android.graphics.Typeface.create("sans-serif",0));input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(10),0,dp(5),0);input.setHint("搜索漫画，发现新故事");input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        if(url.contains("/baozimhapp/search"))input.setText(BaoziSource.query(url,"q",""));
        else if(SiteIndex.isSearch(url))input.setText(SiteIndex.searchQuery(url));
        search.addView(input,new LinearLayout.LayoutParams(0,-1,1));
        TextView scope=ui.chip(prefs.getBoolean("searchBothSources",false)?"双源 ⌄":"当前源 ⌄",false,()->chooseSearchScope(url,input.getText().toString().trim()));scope.setTextSize(11);scope.setPadding(dp(8),0,dp(8),0);search.addView(scope,new LinearLayout.LayoutParams(-2,dp(34)));
        Runnable submit=()->{String q=input.getText().toString().trim();if(q.isEmpty())return;((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
            try{if(q.contains("qimanwu.app")||q.contains("baozimh.com")||q.startsWith("/"))openSiteLink(q);else browse(Sources.baozi(url)?BaoziSource.searchUrl(q):SiteIndex.searchUrl(q),true);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}};
        TextView go=ui.chip("搜索",true,submit);search.addView(go,new LinearLayout.LayoutParams(-2,dp(34)));
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(-1,dp(42));searchLp.setMargins(dp(20),dp(4),dp(20),dp(8));root.addView(search,searchLp);
        input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){submit.run();return true;}return false;});
        HorizontalScrollView channels=new HorizontalScrollView(this);channels.setHorizontalScrollBarEnabled(false);LinearLayout tabs=ui.row();tabs.setPadding(dp(16),0,dp(16),0);channels.addView(tabs);
        String[] labels=Sources.baozi(url)?new String[]{"精选","热门","国漫","韩漫","日漫","分类"}:new String[]{"精选","更新","排行","分类"};
        String[] destinations=Sources.baozi(url)?new String[]{home(),BaoziSource.HOME+"#hot",BaoziSource.HOME+"#index_cn_recommend",BaoziSource.HOME+"#index_kr_recommend",BaoziSource.HOME+"#index_jp_recommend",Sources.category(url)}:new String[]{home(),ORIGIN+"/custom/update",ORIGIN+"/custom/top",Sources.category(url)};
        for(int i=0;i<labels.length;i++){final String destination=destinations[i];boolean selected=url.equals(destination)||(labels[i].equals("分类")&&(url.contains("/classify")||url.contains("/category")));TextView tab=ui.chip(labels[i],selected,()->browse(destination,true));tab.setTextSize(14);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));lp.setMargins(dp(2),dp(3),dp(2),dp(3));tabs.addView(tab,lp);if(selected)channels.post(()->channels.smoothScrollTo(Math.max(0,tab.getLeft()-dp(16)),0));}
        root.addView(channels,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout section=ui.row();section.setPadding(dp(20),dp(7),dp(12),dp(2));heading=ui.text("精选漫画",18,ui.text,true);section.addView(heading,new LinearLayout.LayoutParams(0,dp(30),1));heading.setGravity(Gravity.CENTER_VERTICAL);
        boolean category=url.contains("/classify")||url.contains("/category");boolean qimanSearching=!Sources.baozi(url)&&SiteIndex.isSearch(url);TextView accessory=ui.chip(category?"筛选  ›":qimanSearching?"更新书目":"刷新",false,category?this::showSiteFilters:qimanSearching?()->refreshQimanIndex(url):isQimanHome(url)?this::refreshQimanHome:()->browse(url,false));section.addView(accessory,new LinearLayout.LayoutParams(-2,dp(32)));root.addView(section);
        status=ui.text("正在加载书目…",11,ui.muted,false);status.setPadding(dp(20),dp(2),dp(20),dp(9));status.setMaxLines(2);root.addView(status);
        contentHost=new FrameLayout(this);root.addView(contentHost,new LinearLayout.LayoutParams(-1,0,1));showLoading(contentHost);addNavigation("发现");
        if(isQimanHome(url)){loadQimanHome(url,token,refreshHome);return;}
        if(isSearchUrl(url)&&prefs.getBoolean("searchBothSources",false)){startCombinedSearch(url,token);return;}
        worker.execute(()->{try{SiteIndex.Page page=loadBrowsePage(url);runOnUiThread(()->renderBrowsePage(url,page,token));}catch(Exception e){fail(token,"暂时无法加载："+e.getMessage());}});
    }
    void renderBrowsePage(String url,SiteIndex.Page page,int token){
            if(destroyed||token!=generation)return;
            boolean category=url.contains("/classify")||url.contains("/category");
            for(SiteIndex.Entry entry:page.books)knownBooks.put(entry.url,entry.title);while(knownBooks.size()>1000)knownBooks.remove(knownBooks.keySet().iterator().next());prefs.edit().putString("knownBooks",new org.json.JSONObject(knownBooks).toString()).apply();
            boolean searching=Uri.parse(url).getPath().startsWith("/search/")||Uri.parse(url).getPath().endsWith("/search");
            siteFilters.addAll(page.filters);heading.setText(searching?"搜索结果":url.contains("#hot")?"热门漫画":url.contains("#index_cn")?"国漫精选":url.contains("#index_kr")?"韩漫精选":url.contains("#index_jp")?"日漫精选":category?"漫画分类":url.contains("/custom/update")?"最近更新":url.contains("/custom/top")?"人气排行":"精选漫画");
            status.setText(browseStatus(page));
            if(page.books.isEmpty()){boolean emptyQuery=page.indexSearch&&SiteIndex.searchQuery(url).trim().isEmpty();showState(contentHost,emptyQuery?"输入漫画名开始搜索":"暂时没有找到漫画",page.indexSearch?"已收录 "+page.indexedBooks+" 部，可离线搜书名；点右上角更新书目扫描官网分类":"试试其他关键词，或到分类里找找",()->browse(Sources.category(url),true));return;}
            ComicGrid grid=new ComicGrid(this,page.books,covers,dark,page.previous,page.next,browseGridPage,new ComicGrid.Actions(){public void open(SiteIndex.Entry entry){openSiteLink(entry.url);}public void preview(SiteIndex.Entry entry){previewBook(entry);}public void sourcePage(String address){browse(address,true);}public void pageChanged(int index){browseGridPage=index;}},searching?chapterCounts:null,!isQimanHome(url));
            contentHost.removeAllViews();
            if(isQimanHome(url)){homeRefreshFrame=new PullRefreshFrame(this,grid,grid::canScrollUp,this::refreshQimanHome,ui.muted,ui.surface);homeRefreshFrame.setRefreshing(page.homeRefreshing);contentHost.addView(homeRefreshFrame,new FrameLayout.LayoutParams(-1,-1));}
            else contentHost.addView(grid,new FrameLayout.LayoutParams(-1,-1));
    }
    String browseStatus(SiteIndex.Page page){
        if(page.savedHome){String saved=new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new java.util.Date(page.snapshotTime));return page.sourceNote+" · 共 "+page.books.size()+" 部\n保存于 "+saved;}
        if(!page.sourceNote.isEmpty())return page.sourceNote+" · 共 "+page.books.size()+" 部";
        return page.indexSearch?"本地书目匹配 "+page.books.size()+" 部（已收录 "+page.indexedBooks+" 部）":"共 "+page.books.size()+" 部  ·  长按封面查看简介";
    }
    SiteIndex.Page savedQimanHome()throws IOException {
        synchronized(QimanHomeCache.FILE_LOCK){
            android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"qiman-home.tsv"));
            try{return QimanHomeCache.read(file.openRead());}catch(IOException missingOrCorrupt){return QimanHomeCache.read(getAssets().open("qiman-home.tsv"));}
        }
    }
    void saveQimanHome(SiteIndex.Page page)throws IOException {
        synchronized(QimanHomeCache.FILE_LOCK){
            android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"qiman-home.tsv"));FileOutputStream out=file.startWrite();
            try{QimanHomeCache.write(page,System.currentTimeMillis(),out);file.finishWrite(out);}catch(IOException|RuntimeException e){file.failWrite(out);throw e;}
        }
    }
    boolean isQimanHome(String url){return !Sources.baozi(url)&&URI.create(url).getPath().equals("/");}
    void refreshQimanHome(){
        if(!browsing||!isQimanHome(browseUrl))return;
        if(QIMAN_HOME_REFRESH.isRefreshing()){if(homeRefreshFrame!=null)homeRefreshFrame.setRefreshing(false);Toast.makeText(this,"首页正在同步，请稍候",Toast.LENGTH_SHORT).show();return;}
        browse(browseUrl,false,true);
    }
    void loadQimanHome(String url,int token,boolean requested){
        final boolean sync=QIMAN_HOME_REFRESH.begin(requested);
        if(sync)System.err.println("Jingman homepage sync triggered");
        worker.execute(()->{
          try{
            if(destroyed||token!=generation)return;
            final SiteIndex.Page saved;
            try{saved=savedQimanHome();}catch(IOException e){fail(token,"已保存首页读取失败："+e.getMessage());return;}
            SiteIndex.Page initial=QimanHomeCache.withNote(saved,sync?"已保存首页，正在同步":"已保存首页，下拉可刷新");initial.homeRefreshing=sync;
            runOnUiThread(()->renderBrowsePage(url,initial,token));
            if(!sync)return;
            try{
                if(destroyed||token!=generation)return;
                SiteIndex.Page live=SiteIndex.parse(fetchPassive(url),url);if(live.books.isEmpty())throw new IOException("官网首页未返回书目");
                try{QimanSearchIndex known=qimanIndex();for(int i=0;i<live.books.size();i++){SiteIndex.Entry e=live.books.get(i),match=known.find(e.url);if(e.cover.isEmpty()&&match!=null&&!match.cover.isEmpty())live.books.set(i,new SiteIndex.Entry(e.url,e.title,3,match.cover));}}catch(IOException ignored){}
                try{saveQimanHome(live);}catch(IOException storage){live.sourceNote="官网已更新，本机未能保存首页";}
                runOnUiThread(()->{if(destroyed||token!=generation)return;if(QimanHomeCache.sameBooks(saved,live))status.setText(browseStatus(live));else renderBrowsePage(url,live,token);});
            }catch(IOException unavailable){
                String note=unavailable.getMessage()!=null&&unavailable.getMessage().contains("403")?"应用请求返回403，显示已保存首页":"同步暂未成功，显示已保存首页";
                SiteIndex.Page fallback=QimanHomeCache.withNote(saved,note);
                runOnUiThread(()->{if(!destroyed&&token==generation)status.setText(browseStatus(fallback));});
            }
          }finally{if(sync){QIMAN_HOME_REFRESH.finish();runOnUiThread(()->{if(!destroyed&&token==generation&&homeRefreshFrame!=null)homeRefreshFrame.setRefreshing(false);});}}
        });
    }
    boolean isSearchUrl(String url){return Sources.baozi(url)?Uri.parse(url).getPath().endsWith("/search"):SiteIndex.isSearch(url);}
    String searchQuery(String url){return Sources.baozi(url)?BaoziSource.query(url,"q",""):SiteIndex.searchQuery(url);}
    void chooseSearchScope(String url,String query){
        dialog().setTitle("搜索范围").setSingleChoiceItems(new String[]{"当前来源（"+Sources.name(url)+"）","两个来源（奇漫屋＋包子漫画）"},prefs.getBoolean("searchBothSources",false)?1:0,(d,which)->{
            prefs.edit().putBoolean("searchBothSources",which==1).apply();d.dismiss();
            String target=query.isEmpty()?url:Sources.baozi(url)?BaoziSource.searchUrl(query):SiteIndex.searchUrl(query);
            browseGridPage=0;browse(target,false);
        }).setNegativeButton("取消",null).show();
    }
    void startCombinedSearch(String url,int token){
        final CombinedSearchResults session=new CombinedSearchResults(searchQuery(url));combinedSearch=session;heading.setText("双源搜索结果");
        if(session.query.trim().isEmpty()){session.qimanLoading=false;session.baoziNext=null;renderCombinedSearch(session,token);return;}
        worker.execute(()->{
            try{SiteIndex.Page qiman=loadBrowsePage(SiteIndex.searchUrl(session.query));runOnUiThread(()->{if(destroyed||token!=generation||combinedSearch!=session)return;session.setQiman(qiman.books);renderCombinedSearch(session,token);});}
            catch(Exception e){runOnUiThread(()->{if(destroyed||token!=generation||combinedSearch!=session)return;session.qimanLoading=false;session.qimanError="奇漫屋书目读取失败";renderCombinedSearch(session,token);});}
        });
        loadCombinedBaozi(session,token);
    }
    void loadCombinedBaozi(CombinedSearchResults session,int token){
        if(destroyed||generation!=token||combinedSearch!=session||session.baoziLoading||session.baoziNext==null)return;
        final String address=session.baoziNext;session.baoziLoading=true;session.baoziError="";renderCombinedSearch(session,token);
        worker.execute(()->{
            try{SiteIndex.Page baozi=BaoziSource.parse(fetch(address),address);runOnUiThread(()->{if(destroyed||token!=generation||combinedSearch!=session)return;session.appendBaozi(address,baozi);renderCombinedSearch(session,token);});}
            catch(Exception e){runOnUiThread(()->{if(destroyed||token!=generation||combinedSearch!=session)return;session.baoziLoading=false;session.baoziError="包子漫画暂不可用，可重试";renderCombinedSearch(session,token);});}
        });
    }
    void renderCombinedSearch(CombinedSearchResults session,int token){
        if(destroyed||token!=generation||combinedSearch!=session)return;
        List<SiteIndex.Entry> entries=session.merged();
        heading.setText("双源搜索结果");status.setText("奇漫屋 "+session.qimanCount()+" 部 · 包子漫画 "+session.baoziCount()+" 部 · 合计 "+entries.size()+" 部（已加载）"+(session.qimanLoading?"\n奇漫屋书目加载中":session.baoziLoading?"\n包子漫画搜索中…":!session.qimanError.isEmpty()?"\n"+session.qimanError:!session.baoziError.isEmpty()?"\n"+session.baoziError:""));
        chapterCounts.cancelRequests();LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
        if(entries.isEmpty()){
            FrameLayout empty=new FrameLayout(this);panel.addView(empty,new LinearLayout.LayoutParams(-1,0,1));
            if(session.qimanLoading||session.baoziLoading)showLoading(empty);else showState(empty,session.query.isEmpty()?"输入漫画名开始搜索":"暂时没有找到漫画","可切换当前来源，或试试其他书名关键词",()->browse(Sources.category(browseUrl),true));
        }else{
            ComicGrid grid=new ComicGrid(this,entries,covers,dark,null,null,browseGridPage,new ComicGrid.Actions(){public void open(SiteIndex.Entry entry){openSiteLink(entry.url);}public void preview(SiteIndex.Entry entry){previewBook(entry);}public void sourcePage(String address){}public void pageChanged(int index){browseGridPage=index;}},chapterCounts);
            panel.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        }
        if(session.baoziNext!=null){
            TextView more=ui.action(session.baoziLoading?"包子漫画搜索中…":session.baoziError.isEmpty()?"加载更多包子漫画结果":"重试包子漫画搜索",false,()->loadCombinedBaozi(session,token));more.setEnabled(!session.baoziLoading);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(40));lp.setMargins(dp(20),dp(4),dp(20),dp(6));panel.addView(more,lp);
        }
        contentHost.removeAllViews();contentHost.addView(panel,new FrameLayout.LayoutParams(-1,-1));
    }
    synchronized QimanSearchIndex qimanIndex() throws IOException {
        if(qimanSearchIndex==null){
            QimanSearchIndex index=new QimanSearchIndex(getAssets().open("qiman-catalog.tsv"));
            android.util.AtomicFile saved=new android.util.AtomicFile(new File(getFilesDir(),"qiman-catalog.tsv"));
            try{index.read(saved.openRead());}catch(IOException ignored){}
            qimanSearchIndex=index;
        }
        return qimanSearchIndex;
    }
    SiteIndex.Page loadBrowsePage(String url)throws IOException {
        if(Sources.baozi(url)||!SiteIndex.isSearch(url))return Sources.parse(fetch(url),url);
        SiteIndex.Page page=new SiteIndex.Page();
        QimanSearchIndex index=qimanIndex();
        // Preferences contain both sources; the index validates every comic URL.
        try{org.json.JSONObject saved=new org.json.JSONObject(prefs.getString("knownBooks","{}"));Iterator<String> keys=saved.keys();while(keys.hasNext()){String key=keys.next();index.add(new SiteIndex.Entry(key,saved.getString(key)));}}catch(org.json.JSONException ignored){}
        page.books.addAll(index.search(SiteIndex.searchQuery(url)));page.previous=null;page.next=null;page.indexSearch=true;page.indexedBooks=index.size();return page;
    }
    void refreshQimanIndex(String searchUrl){
        if(qimanUpdating){Toast.makeText(this,"官网书目正在更新，请稍候",Toast.LENGTH_SHORT).show();return;}
        qimanUpdating=true;
        final int token=++generation;status.setText("正在扫描官网书目，可返回暂停，下次更新时继续…");
        worker.execute(()->{
            QimanCatalogScanner scanner=null;
            try{
                InputStream checkpoint=null;
                try{checkpoint=new android.util.AtomicFile(new File(getFilesDir(),"qiman-catalog.tsv")).openRead();}catch(IOException ignored){}
                scanner=new QimanCatalogScanner(qimanIndex(),checkpoint);
                while(scanner.hasNext()){
                    if(destroyed||token!=generation||Thread.currentThread().isInterrupted())return;
                    try{scanner.step(MainActivity::fetchPassive);}catch(IOException unavailable){scanner.deferFailure();}
                    if(scanner.done()%10==0)saveQimanScan(scanner);
                    final int scanned=scanner.done(),remaining=scanner.remaining(),count=scanner.index.size();runOnUiThread(()->{if(!destroyed&&token==generation)status.setText("已扫描 "+scanned+" 页 · 收录 "+count+" 部 · 待查 "+remaining+" 个入口，可返回暂停");});
                }
                saveQimanScan(scanner);
                final int unresolved=scanner.remaining(),books=scanner.index.size();
                runOnUiThread(()->{if(!destroyed&&token==generation){if(unresolved==0)browse(searchUrl,false);else status.setText("已保存 "+books+" 部书目 · "+unresolved+" 页暂不可用，下次更新继续重试");}});
            }catch(Exception e){runOnUiThread(()->{if(!destroyed&&token==generation)status.setText("扫描暂未完成，下次更新继续："+e.getMessage());});}
            finally{
                if(scanner!=null)try{saveQimanScan(scanner);}catch(IOException e){runOnUiThread(()->{if(!destroyed&&token==generation)status.setText("扫描进度未能保存，已有文件保留："+e.getMessage());});}
                qimanUpdating=false;
            }
        });
    }
    void saveQimanScan(QimanCatalogScanner scanner)throws IOException {
        android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"qiman-catalog.tsv"));
        FileOutputStream out=file.startWrite();
        try{scanner.write(out);file.finishWrite(out);}catch(IOException|RuntimeException e){file.failWrite(out);throw e;}
    }
    TextView coverCaption(){TextView text=new TextView(this);text.setTextSize(10);text.setTextColor(Color.GRAY);text.setGravity(Gravity.CENTER);text.setMaxLines(2);return text;}
    String home(){return "baozi".equals(activeSource)?BaoziSource.HOME:ORIGIN+"/";}
    void chooseSource(){
        dialog().setTitle("选择漫画来源").setItems(new String[]{"包子漫画","奇漫屋"},(d,p)->{
            activeSource=p==0?"baozi":"qimanwu";prefs.edit().putString("source",activeSource).apply();siteHistory.clear();browse(home(),false);
        }).setNegativeButton("取消",null).show();
    }
    static String description(String html){
        Matcher m=Pattern.compile("(?is)<meta\\b([^>]*)>").matcher(html);
        while(m.find())if("description".equals(SiteIndex.attribute(m.group(1),"name")))return SiteIndex.attribute(m.group(1),"content");
        return "";
    }

    void showLibrary(boolean history){
        save();browsing=false;shelving=true;mineView=false;returnToLibrary=true;historyView=history;chapter=-1;final int token=++generation;baseScreen();
        tabHeader("书架",history?"故事读到哪里，都替你记着":"收藏喜欢的漫画，慢慢读完");
        LinearLayout tabs=ui.row();tabs.setPadding(dp(16),dp(3),dp(16),dp(8));
        tabs.addView(ui.chip("我的收藏",!history,()->showLibrary(false)),new LinearLayout.LayoutParams(-2,dp(34)));tabs.addView(ui.chip("最近阅读",history,()->showLibrary(true)),new LinearLayout.LayoutParams(-2,dp(34)));root.addView(tabs);
        List<LibraryStore.Book> entries=library.list(!history);status.setText(entries.size()+" 部漫画  ·  长按封面管理");
        contentHost=new FrameLayout(this);root.addView(contentHost,new LinearLayout.LayoutParams(-1,0,1));addNavigation("书架");
        if(entries.isEmpty()){showState(contentHost,history?"还没有阅读记录":"书架等你来填满",history?"打开一话漫画，下次从这里继续":"发现喜欢的漫画后，加入书架就能在这里找到",()->browse(home(),false));return;}
        ArrayList<SiteIndex.Entry> books=new ArrayList<>();for(LibraryStore.Book b:entries)books.add(new SiteIndex.Entry(b.url,b.title,3,b.cover));
        ComicGrid grid=new ComicGrid(this,books,covers,dark,null,null,0,new ComicGrid.Actions(){
            public void open(SiteIndex.Entry b){openSiteLink(b.url);}public void sourcePage(String url){}public void pageChanged(int p){}
            public void preview(SiteIndex.Entry entry){LibraryStore.Book b=library.get(entry.url);dialog().setTitle(b.title).setItems(new String[]{"查看简介",b.favorite?"移出书架，保留进度":"加入书架"},(d,which)->{if(which==0)previewBook(entry);else{library.toggle(b.url);showLibrary(history);}}).setNegativeButton("取消",null).show();}
        });contentHost.addView(grid,new FrameLayout.LayoutParams(-1,-1));
    }
    void showMine(){
        save();++generation;chapter=-1;browsing=false;shelving=false;mineView=true;returnToLibrary=false;baseScreen();tabHeader("我的","让阅读回到简单");
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(8),dp(20),dp(20));scroll.addView(body);
        LinearLayout card=ui.row();card.setPadding(dp(18),dp(20),dp(18),dp(20));card.setBackground(ui.shape(ui.tint,18));FrameLayout mark=new FrameLayout(this);mark.setBackground(ui.shape(ui.surface,16));mark.addView(new ReaderUi.Icon(this,"book",ui.accent),new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));card.addView(mark,new LinearLayout.LayoutParams(dp(56),dp(56)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(1);copy.setPadding(dp(15),0,0,0);copy.addView(ui.text("你的阅读空间",18,ui.text,true));TextView counts=ui.text(library.list(true).size()+" 部收藏   ·   "+library.list(false).size()+" 条阅读记录",11,ui.muted,false);counts.setPadding(0,dp(9),0,0);copy.addView(counts);card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));body.addView(card);
        TextView group=ui.text("阅读与管理",12,ui.muted,false);group.setPadding(dp(3),dp(24),0,dp(12));body.addView(group);
        addMenu(body,"clock","最近阅读","回到上次停下的地方",()->showLibrary(true));
        addMenu(body,"storage","章节缓存","管理已保存的漫画图片",this::manageChapters);
        addMenu(body,"search","奇漫屋本地书目","离线搜索书名，扫描官网更新",this::showQimanCatalog);
        addMenu(body,"source","内容来源",Sources.name(home()),this::chooseSource);
        addMenu(body,"moon","外观",dark?"深色模式":"浅色模式",()->{dark=!dark;prefs.edit().putBoolean("dark",dark).apply();showMine();});
        addMenu(body,"link","打开漫画链接","支持包子漫画与奇漫屋",this::changeBook);
        addMenu(body,"source","检查更新","当前版本 "+appVersion(),()->checkUpdates(true));
        TextView note=ui.text("静漫 "+appVersion()+"\n书架与阅读记录仅保存在本机",11,ui.muted,false);note.setGravity(Gravity.CENTER);note.setLineSpacing(dp(5),1);note.setPadding(0,dp(25),0,dp(10));body.addView(note);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));addNavigation("我的");
    }
    String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "";}}
    void showQimanCatalog(){
        final int token=generation;
        worker.execute(()->{
            try{final int count=qimanIndex().size();runOnUiThread(()->{
                if(destroyed||token!=generation)return;
                dialog().setTitle("奇漫屋本地书目")
                    .setMessage("已收录 "+count+" 部漫画。书名搜索可离线使用，打开目录和正文需要联网。\n\n扫描会读取官网公开分类和分页，只保存书名、漫画链接与封面地址，不下载漫画正文。返回可暂停，下次继续；未公开或未被分类收录的作品可能仍会遗漏。")
                    .setPositiveButton("搜索",(d,w)->browse(SiteIndex.searchUrl(""),true))
                    .setNeutralButton("扫描／继续扫描",(d,w)->{String url=SiteIndex.searchUrl("");browse(url,true);refreshQimanIndex(url);})
                    .setNegativeButton("关闭",null).show();
            });}catch(IOException e){runOnUiThread(()->{if(!destroyed&&token==generation)Toast.makeText(this,"无法读取书目："+e.getMessage(),Toast.LENGTH_LONG).show();});}
        });
    }
    void addMenu(LinearLayout parent,String icon,String title,String subtitle,Runnable action){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);parent.addView(ui.menuRow(icon,title,subtitle,action),lp);}
    void tabHeader(String title,String subtitle){
        LinearLayout top=ui.row();top.setPadding(dp(20),dp(8),dp(12),0);heading=ui.text(title,25,ui.text,true);top.addView(heading,new LinearLayout.LayoutParams(0,dp(49),1));heading.setGravity(Gravity.CENTER_VERTICAL);top.addView(ui.iconButton("more","更多",this::screenMenu),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(top);
        status=ui.text(subtitle,11,ui.muted,false);status.setPadding(dp(20),dp(2),dp(20),dp(12));root.addView(status);
    }
    void addNavigation(String selected){root.addView(ui.bottomBar(selected,()->browse(home(),false),()->showLibrary(false),this::showMine));}
    void showLoading(FrameLayout target){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setGravity(Gravity.CENTER);ProgressBar progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ui.accent));box.addView(progress,new LinearLayout.LayoutParams(dp(26),dp(26)));TextView text=ui.text("正在发现好故事…",12,ui.muted,false);text.setPadding(0,dp(14),0,0);box.addView(text);target.removeAllViews();target.addView(box,new FrameLayout.LayoutParams(-1,-1));}
    void showState(FrameLayout target,String title,String hint,Runnable action){LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setGravity(Gravity.CENTER);box.setPadding(dp(30),dp(12),dp(30),dp(12));box.addView(new ReaderUi.Icon(this,"book",ui.accent),new LinearLayout.LayoutParams(dp(46),dp(46)));TextView name=ui.text(title,17,ui.text,true);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(20),0,dp(10));box.addView(name);TextView detail=ui.text(hint,12,ui.muted,false);detail.setGravity(Gravity.CENTER);detail.setLineSpacing(dp(4),1);box.addView(detail);TextView go=ui.action("去发现",false,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(120),dp(40));lp.topMargin=dp(22);box.addView(go,lp);target.removeAllViews();target.addView(box,new FrameLayout.LayoutParams(-1,-1));}
    AlertDialog.Builder dialog(){return new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,dark?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert));}
    void screenMenu(){
        ArrayList<String> options=new ArrayList<>();if(chapter>=0){options.add("返回章节目录");if(qimanChapters!=null&&qimanChapters.alternatives(paths.get(chapter)).size()>1)options.add("切换本话入口");}options.add("刷新当前页面");options.add("切换内容来源");options.add("打开漫画链接");options.add("管理章节缓存");options.add(dark?"切换浅色模式":"切换深色模式");
        dialog().setTitle("阅读工具").setItems(options.toArray(new String[0]),(d,p)->{String item=options.get(p);if(item.equals("返回章节目录"))catalog();else if(item.equals("切换本话入口"))switchChapterEntry();else if(item.equals("切换内容来源"))chooseSource();else if(item.equals("打开漫画链接"))changeBook();else if(item.equals("管理章节缓存"))manageChapters();else if(item.startsWith("切换")&&item.endsWith("模式")){dark=!dark;prefs.edit().putBoolean("dark",dark).apply();refreshScreen();}else if(browsing&&isQimanHome(browseUrl))refreshQimanHome();else refreshScreen();}).setNegativeButton("取消",null).show();
    }
    void refreshScreen(){if(chapter>=0)read(chapter,readingPath);else if(mineView)showMine();else if(shelving)showLibrary(historyView);else if(browsing)browse(browseUrl,false);else catalog();}
    void previewBook(SiteIndex.Entry entry){
        final int token=generation;Toast.makeText(this,"正在读取详情预览…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{
                String html=fetch(entry.url),name=entry.title,summary=description(html),cover=Sources.detailCover(html,entry.url);int count=0;
                if(Sources.baozi(entry.url)){BaoziSource.Detail detail=BaoziSource.detail(html,entry.url);name=detail.title;summary=detail.author+"\n\n"+detail.summary;count=detail.paths.size();}else count=ChapterTotals.count(html,entry.url);
                final String bookName=name,bookDescription=summary,previewCover=cover;final int chapters=count;
                runOnUiThread(()->{
                    if(destroyed||token!=generation)return;
                    chapterCounts.remember(entry.url,chapters);
                    library.remember(entry.url,bookName,previewCover,bookDescription);
                    ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);
                    ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);body.addView(image,new LinearLayout.LayoutParams(-1,dp(200)));TextView state=coverCaption();body.addView(state);covers.bind(image,previewCover,entry.url,state);
                    body.addView(label(Sources.name(entry.url)+(chapters>0?" · "+chapters+" 话":"")+"\n\n"+(bookDescription.isEmpty()?"暂无简介":bookDescription),15));
                    dialog().setTitle(bookName).setView(scroll).setPositiveButton("打开目录",(d,w)->openSiteLink(entry.url))
                        .setNeutralButton(library.favorite(entry.url)?"移出书架":"加入书架",(d,w)->{library.toggle(entry.url);Toast.makeText(this,library.favorite(entry.url)?"已加入本地书架":"已移出书架，阅读进度保留",Toast.LENGTH_SHORT).show();if(shelving)showLibrary(historyView);})
                        .setNegativeButton("关闭",null).show();
                });
            }catch(Exception e){runOnUiThread(()->{if(!destroyed&&token==generation)Toast.makeText(this,"预览失败："+e.getMessage(),Toast.LENGTH_LONG).show();});}
        });
    }
    void showSiteFilters() {
        final int token=generation;
        final ArrayList<SiteIndex.Entry> available=new ArrayList<>(siteFilters);
        worker.execute(()->{
            try {
                String category=Sources.category(browseUrl);
                ArrayList<SiteIndex.Entry> filters=available.isEmpty()?Sources.parse(fetch(category),category).filters:available;
                runOnUiThread(()->{
                    if(destroyed||token!=generation)return;
                    String[] labels=new String[filters.size()];for(int i=0;i<labels.length;i++)labels[i]=filters.get(i).title;
                    dialog().setTitle("分类 / 状态 / 排序").setItems(labels,(d,p)->browse(filters.get(p).url,true)).setNegativeButton("关闭",null).show();
                });
            }catch(Exception e){fail(token,"分类加载失败，请重试。");}
        });
    }
    void manageChapters() {
        ChapterCache store=ImageLoader.chapters;
        if(store==null) return;
        worker.execute(() -> {
            java.util.List<ChapterCache.Entry> entries=store.list();
            String[] labels=new String[entries.size()];
            for(int i=0;i<labels.length;i++) labels[i]=entries.get(i).toString();
            runOnUiThread(() -> {
                if(destroyed) return;
                long total=0;for(ChapterCache.Entry entry:entries)total+=entry.bytes;
                long limit=prefs.getLong("chapterCacheLimit",0);
                dialog().setTitle(String.format(java.util.Locale.ROOT,"章节缓存 · %.1f MB",total/1048576.0))
                    .setMessage(limit==0?"容量上限：不限 · 点选章节可删除":"容量上限："+(limit/1048576)+" MB · 点选章节可删除")
                    .setItems(labels,(dialog,which) -> worker.execute(() -> {
                        try { store.remove(entries.get(which).id); ImageLoader.clearCache(); }
                        catch(IOException e) { runOnUiThread(() -> Toast.makeText(this,"清理失败："+e.getMessage(),Toast.LENGTH_LONG).show()); }
                        runOnUiThread(() -> { if(!destroyed) manageChapters(); });
                    }))
                    .setNeutralButton("容量上限",(dialog,which)->chooseCacheLimit())
                    .setPositiveButton("清空全部章节缓存",(dialog,which) -> worker.execute(() -> {
                        try { store.clear(); ImageLoader.clearCache(); runOnUiThread(() -> { if(!destroyed) Toast.makeText(this,"章节缓存已清空，阅读进度已保留",Toast.LENGTH_SHORT).show(); }); }
                        catch(IOException e) { runOnUiThread(() -> Toast.makeText(this,"清理失败",Toast.LENGTH_SHORT).show()); }
                    })).setNegativeButton("关闭",null).show();
            });
        });
    }
    void chooseCacheLimit() {
        final long[] limits={0,256L*1024*1024,512L*1024*1024,1024L*1024*1024};
        dialog().setTitle("章节缓存容量上限")
            .setItems(new String[]{"不限（保留现有章节）","256 MB","512 MB","1 GB"},(d,index)->{
                long limit=limits[index];
                if(limit==0){applyCacheLimit(limit);return;}
                dialog().setTitle("自动清理旧章节")
                    .setMessage("超出上限时，按最近使用时间删除旧章节图片；正在保存的章节优先保留。书架和阅读进度保留。设置后会立即清理。")
                    .setPositiveButton("应用并清理",(confirm,which)->applyCacheLimit(limit)).setNegativeButton("取消",null).show();
            }).setNegativeButton("取消",null).show();
    }
    void applyCacheLimit(long limit) {
        worker.execute(()->{
            try { ImageLoader.chapters.setLimit(limit);prefs.edit().putLong("chapterCacheLimit",limit).apply();runOnUiThread(()->{if(!destroyed)manageChapters();}); }
            catch(IOException e){runOnUiThread(()->Toast.makeText(this,"设置失败："+e.getMessage(),Toast.LENGTH_LONG).show());}
        });
    }
    void read(int index) {
        if(index<0 || index>=paths.size()) return;
        String path=paths.get(index);
        if(!Sources.baozi(book)&&qimanChapters!=null){String preferred=prefs.getString(book+path+"/entry",path);if(qimanChapters.alternatives(path).contains(preferred))path=preferred;}
        read(index,path);
    }
    void switchChapterEntry(){
        if(chapter<0||qimanChapters==null)return;
        List<String> choices=qimanChapters.alternatives(paths.get(chapter));
        if(choices.size()<2)return;
        String[] labels=new String[choices.size()];
        for(int i=0;i<labels.length;i++)labels[i]="入口 "+(i+1)+(choices.get(i).equals(readingPath)?"（当前）":"");
        dialog().setTitle("切换本话入口").setItems(labels,(d,p)->read(chapter,choices.get(p))).setNegativeButton("取消",null).show();
    }
    void read(int index,String entryPath) {
        read(index,entryPath,Collections.emptySet());
    }
    void read(int index,String entryPath,Set<String> previousAttempts) {
        if(index<0 || index>=paths.size()) return;
        save(); chapter=index; final int token=++generation;
        final String path=entryPath, key=book+path,readerBook=book;
        readingPath=path;
        final boolean baozi=Sources.baozi(book);
        final Set<String> attempted=new HashSet<>(previousAttempts);attempted.add(path);
        final String nextEntry=baozi||qimanChapters==null?null:qimanChapters.nextAlternative(path,attempted);
        final String preferredKey=book+paths.get(index)+"/entry";
        final java.util.concurrent.atomic.AtomicBoolean imageReceived=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean switchingEntry=new java.util.concurrent.atomic.AtomicBoolean();
        final String cacheTitle=title+" · "+names.get(index);
        prefs.edit().putString(book+"/chapter",path).apply();
        frame(names.get(index)); readerControlsVisible=true;readerAutoHidden=false; status.setText("正在加载漫画…");
        web=new WebView(this); WebView reader=web;
        web.setBackgroundColor(bg());
        web.setOnScrollChangeListener((v,x,y,oldX,oldY)->checkReaderThreshold(reader,token));
        web.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){readerTouchX=event.getX();readerTouchY=event.getY();readerTouchTime=event.getEventTime();readerMoved=false;}
            else if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(Math.abs(event.getX()-readerTouchX)>dp(10)||Math.abs(event.getY()-readerTouchY)>dp(10)||event.getPointerCount()>1)readerMoved=true;}
            else if(event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)readerMoved=true;
            else if(event.getActionMasked()==MotionEvent.ACTION_UP&&!readerMoved&&event.getEventTime()-readerTouchTime<500){
                float x=event.getX()/reader.getScale(),y=event.getY()/reader.getScale();
                reader.evaluateJavascript("(function(){var e=document.elementFromPoint("+x+","+y+");return !!(e&&e.closest('button,a,input'));})()",result->{if(token==generation&&"false".equals(result))setReaderControls(!readerControlsVisible);});
            }
            return false;
        });
        WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setUserAgentString(ImageLoader.USER_AGENT);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(true); settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r) {
                Uri u=r.getUrl(); String p=u.getPath(), host=u.getHost();
                try {
                    if("https".equals(u.getScheme()) && ("s2.325784.xyz".equals(host)||"c-nd3-1.6wm.top".equals(host)||u.toString().equals(BaoziSource.imageUrl(u.toString(),false)))) {
                        ImageLoader.Result result;
                        if("s2.325784.xyz".equals(host)){
                            String direct;
                            try{direct=new String(android.util.Base64.decode(p.substring(1),android.util.Base64.DEFAULT),"UTF-8");}catch(IllegalArgumentException invalid){throw new IOException("图片代理地址无效");}
                            result=ImageLoader.loadQiman(u.toString(),direct,ORIGIN+path,key,cacheTitle);
                        }else result=ImageLoader.load(u.toString(),key,cacheTitle);
                        if(imageReceived.compareAndSet(false,true)&&!baozi&&token==generation)prefs.edit().putString(preferredKey,path).apply();
                        return new WebResourceResponse(result.mime,null,200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(result.bytes));
                    }
                    if("https".equals(u.getScheme())&&"reader.jingman.invalid".equals(host)&&"/reader.js".equals(p))return new WebResourceResponse("application/javascript","UTF-8",getAssets().open("reader.js"));
                    if("www.qimanwu.app".equals(host) && p!=null && p.startsWith("/__reader/")) {
                        String file=p.substring("/__reader/".length());
                        if(Arrays.asList("jquery.js","crypto.js","pic.js","reader.js").contains(file))
                            return new WebResourceResponse("application/javascript","UTF-8",getAssets().open(file));
                    }
                    if("https".equals(u.getScheme()) && p!=null &&
                        (("www.qimanwu.app".equals(host) && (p.startsWith("/attachment/") || p.endsWith("/lazy-read.gif"))) ||
                         "img1.baipiaoguai.org".equals(host) || "s2.325784.xyz".equals(host))) return null;
                } catch(IOException error) {
                    boolean missing=ImageLoader.missing(error);
                    if(missing&&!imageReceived.get()&&nextEntry!=null){
                        if(switchingEntry.compareAndSet(false,true))runOnUiThread(()->{if(!destroyed&&token==generation){Toast.makeText(MainActivity.this,"图片入口已失效，正在尝试本话备用入口",Toast.LENGTH_SHORT).show();read(index,nextEntry,attempted);}});
                    }else if(!switchingEntry.get())fail(token,"图片加载失败："+error.getMessage()+(missing?"。本入口图片已失效，暂无可自动切换的入口。":"。请点重试。"));
                    return new WebResourceResponse("text/plain","UTF-8",502,"Image unavailable",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView v,String url) {
                if(token!=generation) return;
                status.setText("上下滑动阅读 · 轻点屏幕切换菜单");
                pollReaderThreshold(v,token);
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        bar=new LinearLayout(this); root.addView(bar);
        button(bar,"上一话",() -> read(chapter-1)); button(bar,"目录",this::catalog);
        button(bar,"重试",() -> read(chapter,readingPath)); button(bar,"下一话",() -> read(chapter+1));
        worker.execute(() -> {
            try {
                String chapterUrl=(baozi?BaoziSource.ORIGIN:ORIGIN)+path;
                String html=baozi?fetch(chapterUrl):QimanSource.fetch(chapterUrl,true,readerBook),page;
                if(baozi)page=baoziReaderHtml(BaoziSource.images(html),dark);
                else{
                    Matcher m=Pattern.compile("\\bparams\\s*=\\s*'([A-Za-z0-9+/=]+)'").matcher(html);
                    if(!m.find()) throw new IOException("章节数据格式已变化");
                    page=readerHtml(m.group(1),dark,prefs.getInt(key+"/y",0),getResources().getDisplayMetrics().density);
                }
                final String content=page;
                runOnUiThread(() -> { if(!destroyed && token==generation){library.read(book,title,names.get(index));reader.loadDataWithBaseURL(chapterUrl,content,"text/html","UTF-8",null);} });
            } catch(Exception e) { fail(token,"章节加载失败："+e.getMessage()+"。请点重试。"); }
        });
    }
    static String baoziReaderHtml(List<String> images,boolean dark){
        String urls=new org.json.JSONArray(images).toString().replace("<","\\u003c");
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='referrer' content='no-referrer'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            +"<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; script-src 'unsafe-inline' https://reader.jingman.invalid/; style-src 'unsafe-inline'; img-src https: blob: data:\">"
            +"<style>body{margin:0;background:"+(dark?"#101116":"#FAF9FD")+";color:"+(dark?"#F2F1F7":"#21202A")+";font:14px sans-serif}ul{padding:0;margin:0;list-style:none}li{min-height:1px}img{display:block;width:100%;max-width:960px;height:auto;margin:auto}#notice{padding:18px;text-align:center}.retry{display:block;margin:24px auto;padding:16px}</style></head><body><div id='notice'>正在加载图片…</div><ul id='images'></ul>"
            +"<script>window.readerImageUrl=function(x){return x;};</script><script src='https://reader.jingman.invalid/reader.js'></script><script>readerStart({images:"+urls+",source_id:0,windowed:true},null);</script></body></html>";
    }
    static String readerHtml(String data,boolean dark,int y,float density) {
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='referrer' content='no-referrer'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            +"<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; script-src 'unsafe-inline' https://www.qimanwu.app/__reader/; style-src 'unsafe-inline'; img-src https: blob: data:; connect-src https://img1.baipiaoguai.org\">"
            +"<style>body{margin:0;background:"+(dark?"#101116":"#FAF9FD")+";color:"+(dark?"#F2F1F7":"#21202A")+";font:14px sans-serif}ul{padding:0;margin:0;list-style:none}li{min-height:1px}img{display:block;width:100%;max-width:800px;height:auto;margin:auto}#notice{padding:24px;text-align:center;line-height:1.8}.retry{display:block;margin:24px auto;padding:16px}</style></head><body>"
            +"<div id='notice'>正在加载图片…</div><ul id='images'></ul>"
            +"<script>var tpl_path='/template/pc/14/',params='"+data+"';window.readerImageUrl=function(value){return value;};window.onerror=function(){document.getElementById('notice').textContent='图片加载失败，请返回后重试。';};</script>"
            +"<script src='/__reader/jquery.js'></script><script src='/__reader/crypto.js'></script><script src='/__reader/reader.js'></script><script src='/__reader/pic.js'></script></body></html>";
    }
    void setReaderControls(boolean visible){
        if(chapter<0||web==null)return;readerControlsVisible=visible;
        readerTop.setVisibility(visible?View.VISIBLE:View.GONE);status.setVisibility(visible?View.VISIBLE:View.GONE);bar.setVisibility(visible?View.VISIBLE:View.GONE);
        int flags=visible?0:View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if(visible&&!dark){flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;if(Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;}
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    void pollReaderThreshold(WebView reader,int token){
        if(token!=generation||readerAutoHidden||destroyed)return;checkReaderThreshold(reader,token);
        reader.postDelayed(()->pollReaderThreshold(reader,token),700);
    }
    void checkReaderThreshold(WebView reader,int token){
        if(readerAutoHidden||token!=generation)return;
        reader.evaluateJavascript("typeof window.readerPastSecond === 'function' && window.readerPastSecond()",value->{
            if(token==generation&&!readerAutoHidden&&"true".equals(value)){readerAutoHidden=true;setReaderControls(false);}
        });
    }
    void checkUpdates(boolean manual){
        if(manual)Toast.makeText(this,"正在检查更新…",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/QS-Ling/comic-reader/releases/latest").openConnection();
                c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","Jingman-Android");
                StringBuilder data=new StringBuilder();try(BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){char[] b=new char[4096];int n;while((n=in.read(b))!=-1){data.append(b,0,n);if(data.length()>1000000)throw new IOException("更新信息过大");}}finally{c.disconnect();}
                org.json.JSONObject release=new org.json.JSONObject(data.toString());String version=release.optString("tag_name","");
                if(!version.matches("[vV]?[0-9]+(\\.[0-9]+)*"))throw new IOException("版本号格式不受支持");
                if(compareVersions(version,appVersion())<=0){if(manual)runOnUiThread(()->{if(!destroyed)Toast.makeText(this,"当前已是最新版本",Toast.LENGTH_SHORT).show();});return;}
                String link=release.optString("html_url","");org.json.JSONArray assets=release.optJSONArray("assets");
                if(assets!=null)for(int i=0;i<assets.length();i++){org.json.JSONObject a=assets.optJSONObject(i);if(a!=null&&a.optString("name").toLowerCase(Locale.ROOT).endsWith(".apk")){String candidate=a.optString("browser_download_url");if(candidate.startsWith("https://github.com/QS-Ling/comic-reader/releases/download/")){link=candidate;break;}}}
                if(!link.startsWith("https://github.com/QS-Ling/comic-reader/releases/"))throw new IOException("更新链接无效");
                final String target=link,notes=release.optString("body","").trim();
                runOnUiThread(()->{if(!destroyed&&!isFinishing())dialog().setTitle("发现新版本 "+version).setMessage(notes.isEmpty()?"有新版本可供下载。":notes.length()>1500?notes.substring(0,1500)+"…":notes).setPositiveButton("打开下载",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(target)));}catch(ActivityNotFoundException e){Toast.makeText(this,"没有可用的浏览器",Toast.LENGTH_SHORT).show();}}).setNegativeButton("稍后",null).show();});
            }catch(Exception e){if(manual)runOnUiThread(()->{if(!destroyed)Toast.makeText(this,"检查更新失败，请稍后重试",Toast.LENGTH_LONG).show();});}
        });
    }
    static int compareVersions(String a,String b){
        String[] x=a.replaceFirst("^[vV]","").split("\\."),y=b.replaceFirst("^[vV]","").split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++){java.math.BigInteger m=new java.math.BigInteger(i<x.length?x[i]:"0"),n=new java.math.BigInteger(i<y.length?y[i]:"0");int c=m.compareTo(n);if(c!=0)return c;}return 0;
    }
    void save() {
        if(web!=null && chapter>=0 && chapter<paths.size()) prefs.edit().putInt(book+readingPath+"/y",web.getScrollY()).apply();
    }
    void fail(int token,String message) { runOnUiThread(() -> { if(!destroyed && token==generation){status.setText(message);if(contentHost!=null&&chapter<0){showState(contentHost,"暂时没能加载内容",message,this::refreshScreen);View box=contentHost.getChildAt(0);if(box instanceof ViewGroup){ViewGroup group=(ViewGroup)box;View last=group.getChildAt(group.getChildCount()-1);if(last instanceof TextView)((TextView)last).setText("重新加载");}}} }); }
    static String plain(String s) { return android.text.Html.fromHtml(s.replaceAll("<[^>]+>",""),0).toString().trim(); }
    static String fetch(String url) throws IOException {
        return Sources.baozi(url)?BaoziSource.fetch(url,true):QimanSource.fetch(url,true);
    }
    static String fetchPassive(String url) throws IOException {
        return Sources.baozi(url)?BaoziSource.fetch(url,false):QimanSource.fetch(url,false);
    }
    @Override public void onBackPressed() {
        if(chapter>=0)catalog();
        else if(shelving||mineView)browse(home(),false);
        else if(returnToLibrary)showLibrary(historyView);
        else if(!browsing)browse(browseUrl,false);
        else if(!siteHistory.isEmpty())browse(siteHistory.removeLast(),false);
        else super.onBackPressed();
    }
    @Override protected void onPause() { save(); super.onPause(); }
    @Override protected void onStart() { super.onStart();if(root!=null){covers.resume(root);if(chapterCounts!=null)chapterCounts.resume(root);} }
    @Override protected void onStop() {
        super.onStop(); ImageLoader.clearCache();covers.clear();if(chapterCounts!=null)chapterCounts.cancelRequests();
        if(web!=null) web.clearCache(true);
        worker.execute(this::clearDiskCache);
    }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if(level>=TRIM_MEMORY_RUNNING_LOW){ImageLoader.clearCache();covers.clear();} }
    void clearDiskCache() {
        try {
            File base=getCacheDir().getCanonicalFile();
            File[] files=base.listFiles();
            if(files!=null) for(File file:files) deleteCacheFile(base,file);
        } catch(IOException ignored) {}
    }
    void deleteCacheFile(File base,File file) throws IOException {
        String path=file.getCanonicalPath();
        if(!path.startsWith(base.getPath()+File.separator)) return;
        if(file.isDirectory()) { File[] children=file.listFiles(); if(children!=null) for(File child:children) deleteCacheFile(base,child); }
        file.delete();
    }
    @Override protected void onDestroy() { destroyed=true; ++generation;covers.close();if(chapterCounts!=null)chapterCounts.close();if(BaoziSource.guestReader==guestPages){BaoziSource.guestReader=null;BaoziSource.visitorCookies=null;}if(QimanSource.guestReader==qimanGuestReader){QimanSource.guestReader=null;QimanSource.visitorCookies=null;}if(guestPages!=null)guestPages.close(); if(web!=null) web.destroy(); worker.shutdownNow(); super.onDestroy(); }
}
