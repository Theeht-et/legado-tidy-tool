# CI 失败现场（2026-10-31T10:53:41Z）

commit: 697a0ed1eb2b59dde447b5c25f67eb6df4a2b6b5
run: 41194489137

## ./engine/build/test-results/test/TEST-io.github.theeht.legadotidy.engine.MockSiteProbeTest.xml
```
    <failure message="org.opentest4j.AssertionFailedError: Expected value to be true." type="org.opentest4j.AssertionFailedError">org.opentest4j.AssertionFailedError: Expected value to be true.
	at app//org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)
	at app//org.junit.jupiter.api.Assertions.fail(Assertions.java:138)
	at app//kotlin.test.junit5.JUnit5Asserter.fail(JUnitSupport.kt:56)
	at app//kotlin.test.Asserter.assertTrue(Assertions.kt:694)
	at app//kotlin.test.junit5.JUnit5Asserter.assertTrue(JUnitSupport.kt:30)
	at app//kotlin.test.Asserter.assertTrue(Assertions.kt:704)
	at app//kotlin.test.junit5.JUnit5Asserter.assertTrue(JUnitSupport.kt:30)
	at app//kotlin.test.AssertionsKt__AssertionsKt.assertTrue(Assertions.kt:44)
	at app//kotlin.test.AssertionsKt.assertTrue(Unknown Source)
	at app//kotlin.test.AssertionsKt__AssertionsKt.assertTrue$default(Assertions.kt:42)
	at app//kotlin.test.AssertionsKt.assertTrue$default(Unknown Source)
	at app//io.github.theeht.legadotidy.engine.MockSiteProbeTest$达到发现页数上限时不误报完整成功$1.invokeSuspend(MockSiteProbeTest.kt:430)
	at app//kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
	at app//kotlinx.coroutines.internal.ScopeCoroutine.afterResume(Scopes.kt:35)
	at app//kotlinx.coroutines.AbstractCoroutine.resumeWith(AbstractCoroutine.kt:101)
	at app//kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:47)
	at app//kotlinx.coroutines.internal.ScopeCoroutine.afterResume(Scopes.kt:35)
	at app//kotlinx.coroutines.AbstractCoroutine.resumeWith(AbstractCoroutine.kt:101)
	at app//kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:47)
	at app//kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
	at app//kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:256)
	at app//kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:54)
	at app//kotlinx.coroutines.BuildersKt__BuildersKt.runBlockingImpl(Builders.kt:30)
	at app//kotlinx.coroutines.BuildersKt.runBlockingImpl(Unknown Source)
	at app//kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK(Builders.concurrent.kt:172)
	at app//kotlinx.coroutines.BuildersKt.runBlockingK(Unknown Source)
	at app//kotlinx.coroutines.BuildersKt__Builders_concurrentKt.runBlockingK$default(Builders.concurrent.kt:157)
	at app//kotlinx.coroutines.BuildersKt.runBlockingK$default(Unknown Source)
	at app//io.github.theeht.legadotidy.engine.MockSiteProbeTest.达到发现页数上限时不误报完整成功(MockSiteProbeTest.kt:413)
	at java.base@21.0.13/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base@21.0.13/java.util.ArrayList.forEach(ArrayList.java:1596)
	at java.base@21.0.13/java.util.ArrayList.forEach(ArrayList.java:1596)
</failure>
```

