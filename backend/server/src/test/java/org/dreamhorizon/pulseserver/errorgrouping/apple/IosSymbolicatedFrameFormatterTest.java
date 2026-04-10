package org.dreamhorizon.pulseserver.errorgrouping.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IosSymbolicatedFrameFormatterTest {

  @Test
  void format_objc_absolutePath_twoLines_plusOffsetFromRaw() {
    String raw =
        "3   PulseObjCOnlyExample          	0x000000010438a09c -[RootViewController crashTapped] + 24";
    String llvm =
        "-[RootViewController crashTapped] /Users/dev/Pulse/pulse-ios-sdk/Examples/PulseObjCOnlyExample/Sources/RootViewController.m:42:11";
    String out = IosSymbolicatedFrameFormatter.formatSymbolicatedFrameLine(raw, llvm);
    assertTrue(out.lines().findFirst().orElse("").stripLeading().startsWith("3"), out);
    assertTrue(out.contains("PulseObjCOnlyExample"), out);
    assertTrue(out.contains("0x000000010438a09c"));
    assertTrue(out.contains("-[RootViewController crashTapped]"));
    assertTrue(out.contains(" + 24"));
    assertTrue(
        out.contains(
            "\n      at /Users/dev/Pulse/pulse-ios-sdk/Examples/PulseObjCOnlyExample/Sources/RootViewController.m:42:11"));
  }

  @Test
  void format_swift_styleRelativePath_twoLines() {
    String raw = "5   MyApp    0x0000000100ab1234 closure #1 in ViewController.loadView() + 8";
    String llvm = "closure #1 in ViewController.loadView() MainViewController252.swift:100:145";
    String out = IosSymbolicatedFrameFormatter.formatSymbolicatedFrameLine(raw, llvm);
    assertTrue(out.lines().findFirst().orElse("").stripLeading().startsWith("5"), out);
    assertTrue(out.contains("MyApp"), out);
    assertTrue(out.contains("0x0000000100ab1234"));
    assertTrue(out.contains("closure #1 in ViewController.loadView()"));
    assertTrue(out.contains(" + 8"));
    assertTrue(out.contains("\n      at MainViewController252.swift:100:145"));
  }

  @Test
  void format_symbolOnly_singleLine_preservesPlusFromRaw() {
    String raw = "2   App    0x1000 confirmCrash + 212";
    String llvm = "confirmCrash";
    String out = IosSymbolicatedFrameFormatter.formatSymbolicatedFrameLine(raw, llvm);
    assertFalse(out.contains("\n"));
    assertTrue(out.contains("0x1000"));
    assertTrue(out.contains("confirmCrash"));
    assertTrue(out.contains(" + 212"));
  }

  @Test
  void format_rawUnparseable_returnsLlvm() {
    String raw = "not an apple frame line";
    String llvm = "something from llvm";
    assertEquals(llvm, IosSymbolicatedFrameFormatter.formatSymbolicatedFrameLine(raw, llvm));
  }

  @Test
  void format_sameAsRaw_returnsUnchanged() {
    String line = "0   CoreFoundation  0x1804b70e0 __exceptionPreprocess + 160";
    assertEquals(line, IosSymbolicatedFrameFormatter.formatSymbolicatedFrameLine(line, line));
  }

  @Test
  void extractPlusOffset_trailing() {
    assertEquals(
        " + 480",
        IosSymbolicatedFrameFormatter.extractPlusOffset(" -[NSConstantArray objectAtIndex:] + 480"));
    assertEquals("", IosSymbolicatedFrameFormatter.extractPlusOffset("no offset here"));
  }

  @Test
  void format_longImageName_truncated() {
    String longImage = "VeryLongApplicationNameThatExceedsThirtySix";
    String raw = "1 " + longImage + " 0x1 abc + 0";
    String llvm = "symbolic_name /tmp/Foo.m:1:2";
    String out = IosSymbolicatedFrameFormatter.formatSymbolicatedFrameLine(raw, llvm);
    assertTrue(out.contains("..."));
    assertTrue(out.contains("symbolic_name"));
  }
}
