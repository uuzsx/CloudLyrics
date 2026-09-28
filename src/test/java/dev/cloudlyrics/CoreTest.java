package dev.cloudlyrics;

import java.util.List;
import static dev.cloudlyrics.LyricFrame.Cue;

public final class CoreTest {
    static int checks;
    static final List<Cue> CUES = List.of(new Cue(1000,"第一句"),new Cue(3000,"第二句"),
        new Cue(5000,"第一句"),new Cue(7000,""),new Cue(9000,"结束"));
    static LyricFrame frame(String track,long position,boolean playing) {
        return new LyricFrame(track,track,"测试","测试",playing,position,"ready",CUES);
    }
    static void check(Object expected,Object actual,String scenario) {
        checks++;
        if (!java.util.Objects.equals(expected,actual)) throw new AssertionError(scenario+": expected "+expected+", got "+actual);
    }
    public static void main(String[] args) {
        var e = new LineEmitter();
        check(null,e.accept(frame("A",999,true),0),"instrumental intro");
        check("第一句",e.accept(frame("A",1000,true),0),"timestamp boundary");
        check(null,e.accept(frame("A",1200,true),0),"no repeated polling output");
        check(null,e.accept(frame("A",3100,false),0),"pause suppresses output");
        check("第二句",e.accept(frame("A",3100,true),0),"resume picks current line");
        check("第一句",e.accept(frame("A",5100,true),0),"identical words at a later timestamp");
        check("第一句",e.accept(frame("A",1000,true),0),"rewind resets deduplication");
        check("结束",e.accept(frame("A",9300,true),0),"seek emits only current line");
        check("第一句",e.accept(frame("B",1100,true),0),"new song same words");
        check(null,e.accept(frame("B",7100,true),0),"empty interlude cue");
        check(null,e.accept(frame("B",7500,true),0),"empty cue does not spam");
        e.reset();
        check("第一句",e.accept(frame("A",500,true),500),"positive timing adjustment");
        e.reset();
        check(null,e.accept(frame("A",1200,true),-500),"negative timing adjustment");
        check(null,e.accept(LyricFrame.empty("disconnected"),0),"disconnection");
        var duet = new LyricFrame("C","C","","",true,1000,"ready",List.of(new Cue(1000,"甲"),new Cue(1000,"乙"),new Cue(1000,"甲")));
        check("甲 / 乙",e.accept(duet,0),"simultaneous voices merge");
        check("hello world",LineEmitter.sanitize("§chello\nworld"),"literal text sanitation");
        System.out.println("PASS: " + checks + " lyric timing/state checks");
    }
}
