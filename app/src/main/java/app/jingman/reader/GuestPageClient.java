package app.jingman.reader;

import android.app.*;
import android.os.*;
import android.webkit.*;
import android.net.Uri;
import android.widget.*;
import android.view.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** Standard, user-visible visitor verification; no app bridge, account endpoints or ad resources. */
public final class GuestPageClient implements BaoziSource.PageReader {
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WebView browser;
    private AlertDialog dialog;
    private TextView hintLabel;
    private Request pending;
    private volatile boolean closed;
    private static final class Request {
        final String url,referrer;final CountDownLatch done=new CountDownLatch(1);String html;IOException error;int status;
        Request(String url,String referrer){this.url=url;this.referrer=referrer;}
    }
    public GuestPageClient(Activity activity){this.activity=activity;}
    @Override public String read(String url)throws IOException{return read(url,null);}
    public synchronized String read(String url,String referrer)throws IOException{
        if(closed)throw new IOException("页面已关闭");
        Request request=new Request(url,referrer);main.post(()->open(request));
        try{if(!request.done.await(100,TimeUnit.SECONDS)){main.post(()->finish(request,null,new IOException("访客验证超时，可刷新重试")));throw new IOException("访客验证超时，可刷新重试");}}
        catch(InterruptedException e){main.post(()->finish(request,null,new IOException("请求已取消")));Thread.currentThread().interrupt();throw new InterruptedIOException("请求已取消");}
        if(request.error!=null)throw request.error;return request.html;
    }
    private static boolean page(Uri u){return GuestPagePolicy.page(u.toString());}
    private static boolean challenge(Uri u){return GuestPagePolicy.challenge(u.toString());}
    private void open(Request request){
        if(closed||activity.isFinishing()){finish(request,null,new IOException("页面已关闭"));return;}
        if(dialog!=null){dialog.dismiss();dialog=null;}
        pending=request;
        if(browser==null){
            browser=new WebView(activity);WebSettings s=browser.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setGeolocationEnabled(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser,false);
            browser.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest request){request.deny();}});
            browser.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !page(r.getUrl())&&!challenge(r.getUrl());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                    if(challenge(r.getUrl())||r.isForMainFrame()&&page(r.getUrl())&&"GET".equals(r.getMethod()))return null;
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                @Override public void onPageFinished(WebView v,String url){
                    Request target=pending;if(target==null||!GuestPagePolicy.matches(target.url,url))return;
                    v.evaluateJavascript("document.documentElement.outerHTML",value->{
                        if(target!=pending)return;
                        try{Object decoded=new JSONTokener(value).nextValue();if(!(decoded instanceof String))return;String html=(String)decoded;
                            if(html.length()>4_000_000){finish(target,null,new IOException("页面过大"));return;}
                            if(GuestPagePolicy.ready(html,target.url)){CookieManager.getInstance().flush();finish(target,html,null);}
                            else if(target.status==403&&!GuestPagePolicy.hasChallenge(html))finish(target,null,new IOException(Sources.name(target.url)+"拒绝访问，页面未提供访客验证，请稍后重试或切换来源"));
                        }catch(JSONException ignored){}
                    });
                }
                @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame()&&pending!=null&&GuestPagePolicy.matches(pending.url,r.getUrl().toString()))pending.status=response.getStatusCode();}
                @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(r.isForMainFrame()&&pending!=null&&GuestPagePolicy.matches(pending.url,r.getUrl().toString()))finish(pending,null,new IOException("来源验证页面加载失败："+error.getDescription()));}
            });
        }
        if(Sources.baozi(request.url)){String ua=WebSettings.getDefaultUserAgent(activity);browser.getSettings().setUserAgentString(ua);BaoziSource.visitorUserAgent=ua;}
        else browser.getSettings().setUserAgentString(QimanSource.visitorUserAgent);
        if(browser.getParent() instanceof ViewGroup)((ViewGroup)browser.getParent()).removeView(browser);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(1);
        TextView hint=new TextView(activity);hintLabel=hint;hint.setText("来源启用了访客验证。请完成页面验证，通过后会自动返回静漫。无需登录。");hint.setPadding(24,16,24,16);body.addView(hint);
        body.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        dialog=new AlertDialog.Builder(activity).setTitle(Sources.name(request.url)+" · 访客验证").setView(body).setNegativeButton("取消",(d,w)->finish(request,null,new IOException("已取消访客验证"))).create();
        dialog.setOnCancelListener(d->finish(request,null,new IOException("已取消访客验证")));dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*0.88));
        String target=Sources.baozi(request.url)?request.url.split("#",2)[0]:QimanSource.desktopUrl(request.url);
        if(request.referrer==null)browser.loadUrl(target);else browser.loadUrl(target,java.util.Collections.singletonMap("Referer",QimanSource.desktopUrl(request.referrer)));
    }
    private void finish(Request request,String html,IOException error){
        if(request.done.getCount()==0)return;
        if(pending==request){pending=null;if(browser!=null)browser.stopLoading();if(dialog!=null){
            if(error==null||closed||activity.isFinishing()){dialog.dismiss();dialog=null;}
            else{dialog.setTitle(Sources.name(request.url)+" · 访问失败");if(hintLabel!=null)hintLabel.setText((request.status>0?"HTTP "+request.status+"\n":"")+error.getMessage());if(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("关闭");}
        }}
        request.html=html;request.error=error;request.done.countDown();
    }
    public void close(){closed=true;main.post(()->{if(pending!=null)finish(pending,null,new IOException("页面已关闭"));if(dialog!=null){dialog.dismiss();dialog=null;}if(browser!=null){browser.destroy();browser=null;}});}
    public static String clearanceCookies(String url){
        return GuestPagePolicy.clearanceCookies(CookieManager.getInstance().getCookie(url));
    }
}
