# CI 失败现场（2026-11-01T12:52:12Z）

commit: e5c233d37d35975b5aa2fc5a07d70d0a61bad846
run: 41308421160

## ./desktop/build/test-results/test/TEST-io.github.theeht.legadotidy.desktop.ui.WorkflowDecisionsTest.xml
```
    <failure message="org.opentest4j.AssertionFailedError: Expected value to be false." type="org.opentest4j.AssertionFailedError">org.opentest4j.AssertionFailedError: Expected value to be false.
	at app//org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:38)
	at app//org.junit.jupiter.api.Assertions.fail(Assertions.java:138)
	at app//kotlin.test.junit5.JUnit5Asserter.fail(JUnitSupport.kt:56)
	at app//kotlin.test.Asserter.assertTrue(Assertions.kt:694)
	at app//kotlin.test.junit5.JUnit5Asserter.assertTrue(JUnitSupport.kt:30)
	at app//kotlin.test.Asserter.assertTrue(Assertions.kt:704)
	at app//kotlin.test.junit5.JUnit5Asserter.assertTrue(JUnitSupport.kt:30)
	at app//kotlin.test.AssertionsKt__AssertionsKt.assertFalse(Assertions.kt:58)
	at app//kotlin.test.AssertionsKt.assertFalse(Unknown Source)
	at app//kotlin.test.AssertionsKt__AssertionsKt.assertFalse$default(Assertions.kt:56)
	at app//kotlin.test.AssertionsKt.assertFalse$default(Unknown Source)
	at app//io.github.theeht.legadotidy.desktop.ui.WorkflowDecisionsTest.重复组对比不能把已标记删除项重新设为保留(WorkflowDecisionsTest.kt:168)
	at java.base@21.0.13/java.lang.reflect.Method.invoke(Method.java:580)
	at java.base@21.0.13/java.util.ArrayList.forEach(ArrayList.java:1596)
	at java.base@21.0.13/java.util.ArrayList.forEach(ArrayList.java:1596)
</failure>
```

