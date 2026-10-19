package io.github.jahn2007.edifierx.core;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HookGenerationTest {
    static final class Handle {
        final String id,method;boolean removed,failRemove;
        Handle(String id,String method){this.id=id;this.method=method;}
    }
    static HookGeneration<Handle> registry(Handle...old){return new HookGeneration<>(List.of(old),new HookGeneration.Operations<>(){
        public String id(Handle h){return h.id;}
        public void unhook(Handle h){if(h.failRemove)throw new IllegalStateException();h.removed=true;}
    });}
    @Test public void replacesMatchingIdWithoutDuplicateRegistration(){
        var old=new Handle("resume","onResume");var next=new Handle("resume","onResume");var hooks=registry(old);
        assertSame(next,hooks.bind("resume",h->h.method.equals("onResume"),h->{assertSame(old,h);return next;},()->{throw new AssertionError("must atomically replace");}));
        hooks.finish();assertFalse(old.removed);assertFalse(next.removed);
    }
    @Test public void removedAndUnknownHooksAreUnhooked(){var deleted=new Handle("removed","x");var unnamed=new Handle(null,"y");var hooks=registry(deleted,unnamed);hooks.finish();assertTrue(deleted.removed);assertTrue(unnamed.removed);}
    @Test public void changedExecutableCannotReplaceWrongMethod(){var old=new Handle("resume","oldSignature");var next=new Handle("resume","newSignature");var hooks=registry(old);hooks.bind("resume",h->h.method.equals("newSignature"),h->{throw new AssertionError();},()->next);hooks.finish();assertTrue(old.removed);assertFalse(next.removed);}
    @Test public void failedAtomicReplaceUnhooksOldBeforeRegistering(){var old=new Handle("resume","onResume");var hooks=registry(old);hooks.bind("resume",h->true,h->{throw new IllegalStateException();},()->{assertTrue(old.removed);return new Handle("resume","onResume");});hooks.finish();}
    @Test public void rollbackRemovesNewAndUnmatchedOld(){var old=new Handle("removed","x");var next=new Handle("new","y");var hooks=registry(old);hooks.bind("new",h->true,h->{throw new AssertionError();},()->next);hooks.rollback();assertTrue(old.removed);assertTrue(next.removed);}
    @Test public void unhookFailurePreventsDuplicateCreation(){var old=new Handle("resume","x");old.failRemove=true;var hooks=registry(old);assertThrows(IllegalStateException.class,()->hooks.bind("resume",h->true,h->{throw new IllegalStateException();},()->{throw new AssertionError("unsafe duplicate");}));old.failRemove=false;hooks.rollback();assertTrue(old.removed);}
    @Test public void cleanupContinuesAfterOneHandleFailsAndCanBeRetried(){var bad=new Handle("bad","x");bad.failRemove=true;var good=new Handle("good","y");var hooks=registry(bad,good);assertThrows(IllegalStateException.class,hooks::finish);assertTrue(good.removed);bad.failRemove=false;hooks.finish();assertTrue(bad.removed);}
    @Test public void rollbackIncludesAtomicReplacements(){var old=new Handle("resume","x");var next=new Handle("resume","x");var hooks=registry(old);hooks.bind("resume",h->true,h->next,()->{throw new AssertionError();});hooks.rollback();assertFalse(old.removed);assertTrue(next.removed);hooks.rollback();}
}
