package lxmcp.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lxmcp.HeadlessLxTest;

import heronarts.lx.LX;
import heronarts.lx.LXPath;
import heronarts.lx.effect.BlurEffect;
import heronarts.lx.mixer.LXAbstractChannel;
import heronarts.lx.mixer.LXChannel;
import heronarts.lx.mixer.LXPatternEngine;
import heronarts.lx.pattern.color.GradientPattern;
import heronarts.lx.pattern.color.SolidPattern;

class ChannelsTest extends HeadlessLxTest {

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

    assertEquals(Channels.PatternMode.PLAYLIST, info.patternMode());
    for (Channels.PatternInfo pattern : info.patterns()) {
      assertEquals(pattern.active(), pattern.contributing(),
          "in PLAYLIST mode, contributing mirrors active");
    }
  }

  @Test
  void blendModeContributingReflectsEnabledAndCompositeLevel() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    LXPatternEngine engine = channel.getPatternEngine();
    engine.compositeMode.setValue(LXPatternEngine.CompositeMode.BLEND);

    GradientPattern disabledPattern = new GradientPattern(lx);
    channel.addPattern(disabledPattern);
    SolidPattern zeroLevelPattern = new SolidPattern(lx);
    channel.addPattern(zeroLevelPattern);
    GradientPattern visiblePattern = new GradientPattern(lx);
    channel.addPattern(visiblePattern);

    disabledPattern.enabled.setValue(false);
    zeroLevelPattern.enabled.setValue(true);
    zeroLevelPattern.compositeLevel.setValue(0);
    visiblePattern.enabled.setValue(true);
    visiblePattern.compositeLevel.setValue(1);

    Channels.ChannelInfo info = Channels.list(lx).channels().get(channel.getIndex());
    assertEquals(Channels.PatternMode.BLEND, info.patternMode());

    Channels.PatternInfo disabledInfo = findByPath(info, disabledPattern);
    assertFalse(disabledInfo.enabled());
    assertFalse(disabledInfo.contributing(), "disabled pattern does not contribute in BLEND mode");

    Channels.PatternInfo zeroLevelInfo = findByPath(info, zeroLevelPattern);
    assertTrue(zeroLevelInfo.enabled());
    assertEquals(0, zeroLevelInfo.compositeLevel(), 1e-9);
    assertFalse(zeroLevelInfo.contributing(), "zero compositeLevel does not contribute");

    Channels.PatternInfo visibleInfo = findByPath(info, visiblePattern);
    assertTrue(visibleInfo.enabled());
    assertEquals(1, visibleInfo.compositeLevel(), 1e-9);
    assertTrue(visibleInfo.contributing(), "enabled + compositeLevel > 0 contributes in BLEND mode");
  }

  private static Channels.PatternInfo findByPath(Channels.ChannelInfo info,
      heronarts.lx.pattern.LXPattern pattern) {
    String path = pattern.getCanonicalPath();
    return info.patterns().stream()
        .filter(p -> p.path().equals(path))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void mixerControlsExposeCrossfaderAndCuePreview() {
    LX lx = newHeadlessLx();
    lx.engine.mixer.addChannel(); // blendModeOptions/transitionBlendModeOptions are sourced from a channel
    lx.engine.mixer.crossfader.setValue(0.75);
    lx.engine.mixer.cueA.setValue(true);
    lx.engine.mixer.auxB.setValue(true);

    Channels.MixerControls controls = Channels.list(lx).controls();

    assertEquals(0.75, controls.crossfader().value(), 1e-9);
    assertSame(lx.engine.mixer.crossfader, LXPath.get(lx, controls.crossfader().path()));
    assertEquals(lx.engine.mixer.crossfaderBlendMode.getObject().getLabel(),
        controls.crossfaderBlendMode().current());
    assertFalse(controls.crossfaderBlendMode().options().isEmpty());
    assertTrue(controls.cueA().value());
    assertFalse(controls.cueB().value());
    assertFalse(controls.auxA().value());
    assertTrue(controls.auxB().value());
    assertFalse(controls.blendModeOptions().isEmpty());
    assertFalse(controls.transitionBlendModeOptions().isEmpty());
  }

  @Test
  void channelControlsExposeCrossfadeGroupBlendModeAndCueAux() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.crossfadeGroup.setValue(LXAbstractChannel.CrossfadeGroup.A);
    channel.autoMute.setValue(true);
    channel.cueActive.setValue(true);
    channel.auxActive.setValue(true);

    Channels.ChannelInfo info = Channels.list(lx).channels().get(channel.getIndex());
    Channels.ChannelControls controls = info.controls();

    assertEquals("A", controls.crossfadeGroup().current());
    assertNull(controls.crossfadeGroup().options(),
        "crossfadeGroup options are shared, not repeated per channel");
    assertSame(channel.crossfadeGroup, LXPath.get(lx, controls.crossfadeGroup().path()));
    assertEquals(channel.blendMode.getObject().getLabel(), controls.blendMode().current());
    assertNull(controls.blendMode().options(), "blendMode options live once at mixer level");
    assertTrue(controls.autoMute().value());
    assertFalse(controls.isAutoMuted().value());
    assertTrue(controls.cueActive().value());
    assertTrue(controls.auxActive().value());
  }

  @Test
  void patternEngineControlsPresentOnChannelsAbsentOnGroups() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    channel.getPatternEngine().autoCycleEnabled.setValue(true);
    channel.getPatternEngine().autoCycleTimeSecs.setValue(30);
    channel.getPatternEngine().transitionEnabled.setValue(true);

    Channels.ChannelInfo channelInfo = Channels.list(lx).channels().get(channel.getIndex());
    Channels.PatternEngineControls patternEngine = channelInfo.controls().patternEngine();
    assertTrue(patternEngine.autoCycleEnabled().value());
    assertEquals(30, patternEngine.autoCycleTimeSecs().value(), 1e-9);
    assertTrue(patternEngine.transitionEnabled().value());
    assertSame(channel.getPatternEngine().autoCycleEnabled,
        LXPath.get(lx, patternEngine.autoCycleEnabled().path()));
    assertNull(patternEngine.transitionBlendMode().options(),
        "transitionBlendMode options live once at mixer level");

    heronarts.lx.mixer.LXGroup group = lx.engine.mixer.addGroup(
        java.util.List.of(channel));
    Channels.ChannelInfo groupInfo = Channels.list(lx).channels().stream()
        .filter(c -> c.type() == Channels.BusType.GROUP)
        .findFirst()
        .orElseThrow();
    assertNull(groupInfo.controls().patternEngine(), "groups have no pattern engine");
    assertEquals(group.getCanonicalPath(), groupInfo.path());
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

  @Test
  void effectsAreListedOnPattern() {
    LX lx = newHeadlessLx();
    LXChannel channel = lx.engine.mixer.addChannel();
    GradientPattern pattern = new GradientPattern(lx);
    channel.addPattern(pattern);

    Channels.addEffect(lx, pattern.getCanonicalPath(), BlurEffect.class);

    Channels.ChannelInfo info = Channels.list(lx).channels().get(channel.getIndex());
    Channels.PatternInfo patternInfo = findByPath(info, pattern);

    assertFalse(patternInfo.effects().isEmpty(), "pattern effect should be listed");
    assertEquals(1, patternInfo.effects().size());
    Channels.EffectInfo effectInfo = patternInfo.effects().get(0);
    assertEquals(BlurEffect.class.getName(), effectInfo.className());
    assertSame(pattern.getEffects().get(pattern.getEffects().size() - 1), LXPath.get(lx, effectInfo.path()),
        "pattern effect path must round-trip through LXPath");
  }
}
