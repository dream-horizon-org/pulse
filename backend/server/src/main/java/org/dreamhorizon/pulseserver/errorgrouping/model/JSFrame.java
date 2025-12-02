package org.dreamhorizon.pulseserver.errorgrouping.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dreamhorizon.pulseserver.errorgrouping.utils.ErrorGroupingUtils;

@Data
@EqualsAndHashCode(callSuper = true)
public class JSFrame extends Frame {
  private String jsFile;
  private String jsFunction;
  private Integer jsLine;
  private Integer jsColumn;

  @Builder
  public JSFrame(String jsFile,
                 String jsFunction,
                 Integer jsLine,
                 Integer jsColumn,
                 String rawLine,
                 Integer originalPosition) {
    super();
    this.lane = Lane.JS;
    this.token = String.join("#", jsFile, jsFunction);
    this.inApp = ErrorGroupingUtils.isJsInApp(jsFile);
    this.rawLine = rawLine;
    this.originalPosition = (originalPosition != null) ? originalPosition : -1;
    this.jsFile = jsFile;
    this.jsFunction = jsFunction;
    this.jsLine = jsLine;
    this.jsColumn = jsColumn;
  }
}
