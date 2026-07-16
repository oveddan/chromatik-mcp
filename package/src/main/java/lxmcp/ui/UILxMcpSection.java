package lxmcp.ui;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import heronarts.glx.ui.UI2dContainer;
import heronarts.glx.ui.component.UICollapsibleSection;
import heronarts.glx.ui.component.UIIndicator;
import heronarts.glx.ui.component.UILabel;
import heronarts.lx.parameter.LXParameterListener;
import heronarts.lx.studio.LXStudio;

import lxmcp.ServerStatus;

/**
 * Left-pane "LX-MCP" section: connection indicator, server URL, and time of last client
 * activity. Sized to match the stock global sections (UICamera, UIAudio).
 */
class UILxMcpSection extends UICollapsibleSection {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final ServerStatus status;
  private final UILabel lastActivityLabel;
  private final LXParameterListener lastActivityListener;

  UILxMcpSection(LXStudio.UI ui, ServerStatus status) {
    // Stock global sections (UICamera, UIAudio) size themselves to
    // global.getContentWidth() rather than a hand-derived literal — match that.
    super(ui, 0, 0, ui.leftPane.global.getContentWidth(), 0);
    float sectionWidth = ui.leftPane.global.getContentWidth();
    this.status = status;
    setTitle("LX-MCP");
    setLayout(UI2dContainer.Layout.VERTICAL, 4);

    UI2dContainer connectionRow = UI2dContainer.newHorizontalContainer(16, 4,
        new UIIndicator(ui, 12, 12, status.connected),
        new UILabel(0, 0, sectionWidth - 20, UILabel.DEFAULT_HEIGHT, "Client"));
    connectionRow.addToContainer(this);

    UILabel urlLabel =
        new UILabel(0, 0, sectionWidth, UILabel.DEFAULT_HEIGHT, status.url()).setBreakLines(true, true);
    urlLabel.setFont(ui.theme.getControlFont());
    urlLabel.addToContainer(this);

    this.lastActivityLabel =
        new UILabel(0, 0, sectionWidth, UILabel.DEFAULT_HEIGHT, lastActivityText(status.lastActivityMs.getValue()));
    this.lastActivityLabel.addToContainer(this);

    this.lastActivityListener = p -> this.lastActivityLabel.setLabel(lastActivityText(status.lastActivityMs.getValue()));
    status.lastActivityMs.addListener(this.lastActivityListener);
  }

  // Renders an absolute local time rather than a relative "Xs ago": lastActivityMs is a
  // MutableParameter that only fires listeners on change, so a relative string would
  // freeze at whatever it was on the last activity and silently go stale while a client
  // stays idle. An absolute timestamp is still truthful without needing to re-render.
  private static String lastActivityText(double lastActivityMs) {
    if (lastActivityMs <= 0) {
      return "Last activity: never";
    }
    LocalTime time = Instant.ofEpochMilli((long) lastActivityMs).atZone(ZoneId.systemDefault()).toLocalTime();
    return "Last activity: " + TIME_FORMAT.format(time);
  }

  @Override
  public void dispose() {
    this.status.lastActivityMs.removeListener(this.lastActivityListener);
    super.dispose();
  }
}
