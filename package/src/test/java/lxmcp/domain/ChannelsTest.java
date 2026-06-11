package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.effect.BlurEffect;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.model.GridModel;
import heronarts.lx.pattern.color.GradientPattern;

class ChannelsTest {

  private LX lx;

  private LX newHeadlessLx() {
    this.lx = new LX(new GridModel(8, 8));
    return this.lx;
  }

  @AfterEach
  void tearDown() {
    if (this.lx != null) {
      this.lx.dispose();
      this.lx = null;
    }
  }

  @Test
  void snapshotMatchesMixerState() {
    LX lx = newHeadlessLx();
    LXChannel added = lx.engine.mixer.addChannel();

    Channels.MixerInfo mixer = Channels.list(lx);
    assertEquals(lx.engine.mixer.channels.size(), mixer.channels().size());

    Channels.ChannelInfo info = mixer.channels().get(added.getIndex());
    assertEquals(added.getLabel(), info.label());
    assertEquals(added.getIndex(), info.index());
    assertEquals(Channels.BusType.CHANNEL, info.type());
    assertEquals(added.enabled.isOn(), info.enabled());
    assertEquals(added.fader.getValue(), info.fader(), 1e-9);
    assertSame(added, LXPath.get(lx, info.path()), "channel path must round-trip through LXPath");
  }

  @Test
  void patternsCarryActiveFlagAndResolvablePaths() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addPattern(new GradientPattern(lx));

    Channels.ChannelInfo info = Channels.list(lx).channels().get(channel.getIndex());
    assertEquals(channel.patterns.size(), info.patterns().size());

    Channels.PatternInfo last = info.patterns().get(info.patterns().size() - 1);
    assertEquals(GradientPattern.class.getName(), last.className());
    assertSame(channel.patterns.get(channel.patterns.size() - 1), LXPath.get(lx, last.path()),
        "pattern path must round-trip through LXPath");

    long activeCount = info.patterns().stream().filter(Channels.PatternInfo::active).count();
    assertEquals(1, activeCount, "exactly one pattern is active on a non-empty channel");
  }

  @Test
  void effectsAreListedOnChannelAndMaster() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.addEffect(new BlurEffect(lx));
    lx.engine.mixer.masterBus.addEffect(new BlurEffect(lx));

    Channels.MixerInfo mixer = Channels.list(lx);

    Channels.ChannelInfo info = mixer.channels().get(channel.getIndex());
    assertFalse(info.effects().isEmpty(), "channel effect should be listed");
    assertEquals(BlurEffect.class.getName(), info.effects().get(info.effects().size() - 1).className());

    assertFalse(mixer.master().effects().isEmpty(), "master effect should be listed");
    assertTrue(mixer.master().path().startsWith("/lx"), "master path should be canonical");
  }
}
