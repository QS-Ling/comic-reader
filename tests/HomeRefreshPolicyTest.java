package app.jingman.reader;
public class HomeRefreshPolicyTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        HomeRefreshPolicy policy=new HomeRefreshPolicy();
        check(policy.claimStartup(),"first app launch may sync");check(!policy.claimStartup(),"activity recreation does not count as another launch");
        check(policy.begin(true),"startup starts one sync");check(!policy.begin(true),"duplicate gesture/button while in flight is ignored");policy.finish();
        for(int i=0;i<20;i++)check(!policy.begin(false),"returning home must not fetch");
        check(policy.begin(true),"explicit pull starts sync");policy.finish();
        check(policy.begin(true),"later explicit refresh allowed");policy.finish();check(!policy.isRefreshing(),"success/failure releases busy state");
        System.out.println("Homepage refresh policy passed: one startup sync, no sync on repeated navigation, explicit manual sync and in-flight deduplication.");
    }
}
