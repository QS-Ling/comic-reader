package app.jingman.reader;

import android.content.Context;
import android.view.*;
import android.widget.*;

/** Vertical pull-to-refresh without intercepting normal grid scrolling or horizontal gestures. */
public final class PullRefreshFrame extends FrameLayout {
    public interface ScrollCheck {boolean canScrollUp();}
    private final TextView hint;private final Runnable refresh;private final ScrollCheck scroll;
    private View content;private float startX,startY,distance;private boolean dragging,refreshing;
    private final int threshold,slop,header;
    public PullRefreshFrame(Context context,View content,ScrollCheck scroll,Runnable refresh,int textColor,int background){
        super(context);this.content=content;this.scroll=scroll;this.refresh=refresh;
        float density=getResources().getDisplayMetrics().density;threshold=(int)(72*density);header=(int)(48*density);slop=ViewConfiguration.get(context).getScaledTouchSlop();
        hint=new TextView(context);hint.setTextSize(12);hint.setTextColor(textColor);hint.setBackgroundColor(background);hint.setGravity(Gravity.CENTER);hint.setVisibility(GONE);addView(hint,new FrameLayout.LayoutParams(-1,header,Gravity.TOP));
        addView(content,new FrameLayout.LayoutParams(-1,-1));
    }
    public void setRefreshing(boolean value){refreshing=value;dragging=false;distance=0;hint.setText("正在刷新首页…");hint.setVisibility(value?VISIBLE:GONE);content.setTranslationY(value?header:0);}
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){
        if(refreshing)return false;
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();distance=0;dragging=false;}
        else if(event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE){if(scroll.canScrollUp()){startX=event.getX();startY=event.getY();return false;}float dy=event.getY()-startY,dx=Math.abs(event.getX()-startX);if(dy>slop&&dy>dx*1.5f){dragging=true;return true;}}
        else if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)dragging=false;
        return false;
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        if(!dragging)return super.onTouchEvent(event);
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_MOVE){distance=Math.max(0,event.getY()-startY);hint.setVisibility(VISIBLE);hint.setText(distance>=threshold?"松开刷新首页":"下拉刷新首页");content.setTranslationY(Math.min(distance/3,header*1.5f));return true;}
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP){boolean trigger=distance>=threshold;setRefreshing(trigger);if(trigger)refresh.run();return true;}
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){setRefreshing(false);return true;}
        return true;
    }
}
