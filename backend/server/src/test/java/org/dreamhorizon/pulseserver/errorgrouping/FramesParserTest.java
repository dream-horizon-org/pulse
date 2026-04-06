package org.dreamhorizon.pulseserver.errorgrouping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dreamhorizon.pulseserver.errorgrouping.model.Lane;
import org.dreamhorizon.pulseserver.errorgrouping.model.ParsedFrames;
import org.junit.jupiter.api.Test;

class FramesParserTest {
  @Test
  void shouldParseAppleNullSymbolAsAddressFrame() {
    List<String> lines = List.of(
        "Thread 0 Crashed:",
        "29  (null) 0x0000000102c813d8 0x0 + 4341634008",
        "Thread 1:"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getIosNativeFrames().size());
    assertEquals("(null)", parsed.getIosNativeFrames().get(0).getNdkLib());
    assertEquals("0x0000000102c813d8", parsed.getIosNativeFrames().get(0).getNdkPc());
    assertNull(parsed.getIosNativeFrames().get(0).getNdkSymbol());
  }

  @Test
  void shouldStillParseAndroidNdkFrames() {
    List<String> lines = List.of(
        "#00 pc 0000000000012345 /data/app/libnative.so (nativeFunc+12)"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getNdkFrames().size());
    assertEquals("libnative.so", parsed.getNdkFrames().get(0).getNdkLib());
    assertEquals("0000000000012345", parsed.getNdkFrames().get(0).getNdkPc());
    assertEquals("nativeFunc", parsed.getNdkFrames().get(0).getNdkSymbol());
  }

  @Test
  void shouldPreferAndroidNdkPatternWhenBothAppleAndAndroidLikeDataExist() {
    List<String> lines = List.of(
        "#00 pc 0000000000012345 /data/app/libnative.so (nativeFunc+12)",
        "Thread 0 Crashed:",
        "0   PulseIOSExample                0x000000010294248c swift_symbol_one + 164",
        "Thread 1:"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getNdkFrames().size());
    assertEquals(1, parsed.getIosNativeFrames().size());
    assertEquals("libnative.so", parsed.getNdkFrames().get(0).getNdkLib());
    assertEquals("PulseIOSExample", parsed.getIosNativeFrames().get(0).getNdkLib());
  }

  @Test
  void shouldNotAffectJsParsingWhenAppleRulesAreAdded() {
    List<String> lines = List.of(
        "TypeError: undefined is not an object",
        "    at render (index.android.bundle:123:45)",
        "    at anonymous (index.android.bundle:456:78)"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(2, parsed.getJsFrames().size());
    assertEquals(0, parsed.getNdkFrames().size());
    assertEquals(0, parsed.getIosNativeFrames().size());
    assertEquals("render", parsed.getJsFrames().get(0).getJsFunction());
    assertEquals("index.android.bundle", parsed.getJsFrames().get(0).getJsFile());
  }

  @Test
  void shouldParseJsAndAndroidNdkTogetherWithoutInterference() {
    List<String> lines = List.of(
        "TypeError: undefined is not an object",
        "    at render (index.android.bundle:123:45)",
        "#00 pc 0000000000012345 /data/app/libnative.so (nativeFunc+12)"
    );

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(1, parsed.getJsFrames().size());
    assertEquals(1, parsed.getNdkFrames().size());
    assertEquals("render", parsed.getJsFrames().get(0).getJsFunction());
    assertEquals("libnative.so", parsed.getNdkFrames().get(0).getNdkLib());
  }

  @Test
  void shouldParseRealisticAppleCrashReport() {
    List<String> lines = REALISTIC_APPLE_CRASH_REPORT.lines().toList();

    ParsedFrames parsed = FramesParser.parse(lines);

    assertEquals(32, parsed.getIosNativeFrames().size());
    assertEquals(0, parsed.getNdkFrames().size());
    assertEquals("CoreFoundation", parsed.getIosNativeFrames().get(0).getNdkLib());
    assertEquals("0x000000010107c638", parsed.getIosNativeFrames().get(3).getNdkPc());
    assertEquals(
        "$s15PulseIOSExample18MainViewControllerC22crashNSExceptionTapped33_0C0091EDD96CE7CF640B1457C7517B58LLyyFyycfU_",
        parsed.getIosNativeFrames().get(3).getNdkSymbol());
    assertEquals("(null)", parsed.getIosNativeFrames().get(30).getNdkLib());
    assertEquals("(null)", parsed.getIosNativeFrames().get(31).getNdkLib());
    assertFalse(parsed.getIosNativeFrames().stream()
        .anyMatch(frame -> "0x00000001010c0b70".equals(frame.getNdkPc())));
  }

  private static final String REALISTIC_APPLE_CRASH_REPORT = """
Incident Identifier: 710C507B-3D04-424D-9B5A-68623134F05F
CrashReporter Key:   751e03c88caa95616b1ec3fb12f496f54db2f4de
Hardware Model:      iPhone17,1
Process:             PulseIOSExample [99739]
Path:                /Users/hemantgehlot/Library/Developer/CoreSimulator/Devices/60F109DE-477A-4FFE-A321-A1B96616A2BE/data/Containers/Bundle/Application/5232BF2B-B7CD-4F51-9C1E-F867CCB4749F/PulseIOSExample.app/PulseIOSExample
Identifier:          com.pulse.ios.example
Version:             1.0 (1)
Code Type:           ARM-64 (Native)
Role:                Foreground
Parent Process:      launchd [74288]

Date/Time:           2026-03-24 14:25:27.461 +0530
OS Version:          iOS 18.0 (24G222)
Report Version:      104

Exception Type:  EXC_CRASH (SIGABRT)
Exception Codes: 0x00000000 at 0x0000000000000000
Triggered by Thread:  0

Application Specific Information:
*** Terminating app due to uncaught exception 'TestCrashException', reason: 'Test Obj-C exception from Pulse iOS SDK'

Thread 0 Crashed:
0   CoreFoundation                	0x00000001804b70e0 __exceptionPreprocess + 160
1   libobjc.A.dylib               	0x000000018008ede8 objc_exception_throw + 72
2   CoreFoundation                	0x00000001804b6c80 -[NSException raise] + 12
3   PulseIOSExample.debug.dylib   	0x000000010107c638 $s15PulseIOSExample18MainViewControllerC22crashNSExceptionTapped33_0C0091EDD96CE7CF640B1457C7517B58LLyyFyycfU_ + 212
4   PulseIOSExample.debug.dylib   	0x000000010107f918 $s15PulseIOSExample18MainViewControllerC12confirmCrash33_0C0091EDD96CE7CF640B1457C7517B58LL4type6actionySS_yyctFySo13UIAlertActionCcfU_ + 72
5   PulseIOSExample.debug.dylib   	0x000000010107f8a8 $sSo13UIAlertActionCIegg_ABIeyBy_TR + 72
6   UIKitCore                     	0x0000000184f23ce8 -[UIAlertController _invokeHandlersForAction:] + 80
7   UIKitCore                     	0x0000000184f243fc __103-[UIAlertController _dismissAnimated:triggeringAction:triggeredByPopoverDimmingView:dismissCompletion:]_block_invoke_2 + 28
8   UIKitCore                     	0x0000000185248968 -[UIPresentationController transitionDidFinish:] + 788
9   UIKitCore                     	0x000000018524c5e8 __77-[UIPresentationController runTransitionForCurrentStateAnimated:handoffData:]_block_invoke.89 + 340
10  UIKitCore                     	0x000000018535b3f8 -[_UIViewControllerTransitionContext completeTransition:] + 180
11  UIKitCore                     	0x0000000186011ca4 __UIVIEW_IS_EXECUTING_ANIMATION_COMPLETION_BLOCK__ + 28
12  UIKitCore                     	0x0000000186011efc -[UIViewAnimationBlockDelegate _didEndBlockAnimation:finished:context:] + 592
13  UIKitCore                     	0x0000000185fe83b8 -[UIViewAnimationState sendDelegateAnimationDidStop:finished:] + 212
14  UIKitCore                     	0x0000000185fe87e0 -[UIViewAnimationState animationDidStop:finished:] + 188
15  UIKitCore                     	0x0000000185fe8850 -[UIViewAnimationState animationDidStop:finished:] + 300
16  QuartzCore                    	0x000000018b079b1c _ZL23run_animation_callbacksPv + 128
17  libdispatch.dylib             	0x0000000180178de0 _dispatch_client_callout + 16
18  libdispatch.dylib             	0x0000000180187c60 _dispatch_main_queue_drain + 1272
19  libdispatch.dylib             	0x0000000180187758 _dispatch_main_queue_callback_4CF + 40
20  CoreFoundation                	0x000000018041ae3c __CFRUNLOOP_IS_SERVICING_THE_MAIN_DISPATCH_QUEUE__ + 12
21  CoreFoundation                	0x0000000180415534 __CFRunLoopRun + 1944
22  CoreFoundation                	0x0000000180414960 CFRunLoopRunSpecific + 536
23  GraphicsServices              	0x0000000190183b10 GSEventRunModal + 160
24  UIKitCore                     	0x0000000185aa2b40 -[UIApplication _run] + 796
25  UIKitCore                     	0x0000000185aa6d38 UIApplicationMain + 124
26  UIKitCore                     	0x0000000184e9a184 block_destroy_helper.22 + 9660
27  PulseIOSExample.debug.dylib   	0x000000010106e7bc $sSo21UIApplicationDelegateP5UIKitE4mainyyFZ + 128
28  PulseIOSExample.debug.dylib   	0x000000010106e72c $s15PulseIOSExample11AppDelegateC5$mainyyFZ + 44
29  PulseIOSExample.debug.dylib   	0x000000010106e868 main + 28
30  (null)	0x0000000101179410 0x0 + 4313289744
31  (null)	0x00000001013beb98 0x0 + 4315671448

Thread 1 name:  com.apple.uikit.eventfetch-thread
Thread 1:
  """;

  @Test
  void parsesReactNativeJavascriptExceptionHeader() {
    List<String> lines = List.of(
        "com.facebook.react.common.JavascriptException: Error: hello",
        "    at foo (index.js:1:1)"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertTrue(p.isReactNativeJsException());
    assertEquals(Lane.JS, p.getPrimaryExceptionLane());
  }

  @Test
  void parsesRnCompactFrameWithColumn() {
    List<String> lines = List.of("myFunc@main.jsbundle:12:34");
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(1, p.getJsFrames().size());
    assertEquals("myFunc", p.getJsFrames().get(0).getJsFunction());
    assertEquals(12, p.getJsFrames().get(0).getJsLine());
  }

  @Test
  void parsesRnCompactFrameWithoutColumn() {
    List<String> lines = List.of(
        "Error: x",
        "myFunc@main.jsbundle:42"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(1, p.getJsFrames().size());
    assertEquals(1, p.getJsFrames().get(0).getJsLine());
  }

  @Test
  void parsesJavaAtLineAndCausedBy() {
    List<String> lines = List.of(
        "java.lang.RuntimeException: top",
        "Caused by: java.lang.IllegalStateException: nested",
        "    at com.foo.Bar.baz(Bar.java:10)"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertFalse(p.getJavaTypes().isEmpty());
    assertEquals(1, p.getJavaFrames().size());
    assertEquals("com.foo.Bar", p.getJavaFrames().get(0).getJavaClass());
  }

  @Test
  void parsesJsStandardAtFileLineCol() {
    List<String> lines = List.of(
        "TypeError: oops",
        "    at index.android.bundle:3:4"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(1, p.getJsFrames().size());
    assertEquals("anonymous", p.getJsFrames().get(0).getJsFunction());
  }

  @Test
  void parsesNdkTombstoneSignalWithoutAppleExcPrefix() {
    List<String> lines = List.of(
        "signal 6 (SIGABRT)",
        "#00 pc 0000000000001111 /data/lib.so (x+1)"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertFalse(p.getNdkTypes().isEmpty());
  }

  @Test
  void iosExceptionTypeSetsLane() {
    List<String> lines = List.of(
        "Exception Type:  EXC_BAD_ACCESS (SIGSEGV)",
        "Thread 0 Crashed:",
        "0   App   0x0000000100000000 start + 1",
        "Thread 1:"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(Lane.IOS_NATIVE, p.getPrimaryExceptionLane());
    assertFalse(p.getIosNativeTypes().isEmpty());
  }

  @Test
  void javaHeadlineWhenNotJs() {
    List<String> lines = List.of(
        "java.lang.IllegalStateException: boom",
        "    at a.b.C.m(C.java:1)"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(Lane.JAVA, p.getPrimaryExceptionLane());
  }

  @Test
  void invariantViolationSetsJsLane() {
    List<String> lines = List.of(
        "Invariant Violation: navigation state",
        "    at foo (bundle.js:1:1)"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(Lane.JS, p.getPrimaryExceptionLane());
  }

  @Test
  void parsesJsAtFileLineColWithoutAtPrefix() {
    List<String> lines = List.of(
        "Error: e",
        "index.js:10:20"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertEquals(1, p.getJsFrames().size());
    assertEquals(10, p.getJsFrames().get(0).getJsLine());
  }

  @Test
  void capturesIosProcessName() {
    List<String> lines = List.of(
        "Process:             MyApp [12345]",
        "Exception Type:  EXC_CRASH (SIGABRT)",
        "Thread 0 Crashed:",
        "0   MyApp   0x1 x + 1",
        "Thread 1:"
    );
    ParsedFrames p = FramesParser.parse(lines);
    assertTrue(p.getIosNativeFrames().get(0).isInApp());
  }
}
