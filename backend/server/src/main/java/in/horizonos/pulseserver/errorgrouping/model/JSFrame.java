package in.horizonos.pulseserver.errorgrouping.model;

import in.horizonos.pulseserver.errorgrouping.utils.ErrorGroupingUtils;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
