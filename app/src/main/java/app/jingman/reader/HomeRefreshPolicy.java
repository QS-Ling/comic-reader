package app.jingman.reader;

/** One automatic refresh per process startup; subsequent refreshes require an explicit gesture/button. */
public final class HomeRefreshPolicy {
    private boolean startupClaimed,refreshing;
    public synchronized boolean claimStartup(){if(startupClaimed)return false;startupClaimed=true;return true;}
    public synchronized boolean begin(boolean requested){if(!requested||refreshing)return false;refreshing=true;return true;}
    public synchronized boolean isRefreshing(){return refreshing;}
    public synchronized void finish(){refreshing=false;}
}
