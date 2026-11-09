"""Interactive mockups of the timeline legend: the pane content fades and labelled arrows explain
the timeline, on first visit and whenever the timeline is touched; the threshold labels replace
the drag tooltip. Also a shift worker's night across noon (the axis flips to midnight-midnight)."""
import sys

out_dir = sys.argv[1]
src = open(__file__.replace('legend_mockups.py', 'mode_mockups.py')).read()
ns = {'sys': sys, '__file__': __file__.replace('legend_mockups.py', 'mode_mockups.py')}
argv, sys.argv = sys.argv, ['x', '/nonexistent']
exec(src[:src.index("def page(")], ns)
sys.argv = argv
HEAD, STATUS, NAV, TOGGLE, REST, BUTTON, HEADER, SPACER = (
    ns[k] for k in ['HEAD', 'STATUS', 'NAV', 'TOGGLE', 'REST', 'BUTTON', 'HEADER', 'SPACER'])

TIMELINE = '''      <aside onClick="{{showLegend}}" aria-label="{{tlLabel}}" style="flex: none; width: 34px; position: relative; cursor: pointer">
        <div data-track="1" style="position: absolute; top: 4px; bottom: 4px; left: 3px; width: 28px">
          <div style="position: absolute; inset: 0; border-radius: 14px; background: {{tlGradient}}"></div>
          <sc-if value="{{showNap}}" hint-placeholder-val="{{ true }}">
            <div style="position: absolute; top: {{napTop}}; height: {{napHeight}}; min-height: 3px; left: 4px; right: 4px; border-radius: 3px; background: {{napColor}}"></div>
          </sc-if>
          <sc-if value="{{showSleep}}" hint-placeholder-val="{{ false }}">
            <div style="position: absolute; top: {{sleepTop}}; height: {{sleepHeight}}; left: 4px; right: 4px; border-radius: 3px; background: {{sleepColor}}"></div>
          </sc-if>
          <sc-for list="{{suns}}" as="p" hint-placeholder-count="2">
            <div aria-hidden="true" style="position: absolute; top: {{p.top}}; left: 50%; width: 30px; height: 30px; margin: -15px 0 0 -15px; border-radius: 15px; background: radial-gradient(circle, rgba(238, 190, 105, 0.95) 0%, rgba(238, 190, 105, 0.6) 42%, rgba(238, 190, 105, 0) 72%); filter: blur(0.5px)"></div>
          </sc-for>
          <sc-for list="{{moons}}" as="p" hint-placeholder-count="1">
            <svg aria-hidden="true" width="28" height="28" viewBox="0 0 24 24" style="position: absolute; top: {{p.top}}; left: 50%; margin: -14px 0 0 -14px; opacity: 0.8; filter: blur(0.4px)"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" fill="#A9BFE6"></path></svg>
          </sc-for>
          <sc-for list="{{hours}}" as="h" hint-placeholder-count="9">
            <span style="position: absolute; top: {{h.top}}; left: 0; right: 0; transform: translateY(-50%); text-align: center; font-size: 11px; font-weight: 400; color: {{h.color}}">{{h.label}}</span>
          </sc-for>
          <div onPointerDown="{{dragStartStart}}" style="position: absolute; top: calc({{startTop}} - 14px); left: -6px; right: -6px; height: 28px; cursor: ns-resize; touch-action: none">
            <div style="position: absolute; top: 8px; left: 3px; right: 3px; height: 12px; box-sizing: border-box; border-radius: 6px; background: #FFFFFF; border: 2px solid #1F7A91; box-shadow: {{startHalo}}"></div>
          </div>
          <div onPointerDown="{{dragStartEnd}}" style="position: absolute; top: calc({{endTop}} - 14px); left: -6px; right: -6px; height: 28px; cursor: ns-resize; touch-action: none">
            <div style="position: absolute; top: 8px; left: 3px; right: 3px; height: 12px; box-sizing: border-box; border-radius: 6px; background: #FFFFFF; border: 2px solid #1F7A91; box-shadow: {{endHalo}}"></div>
          </div>
        </div>
      </aside>
'''

# The legend sits over the pane content, in the timeline's coordinates (track: 8px from the
# row's top, 12px from its bottom), arrows pointing back at the track
LEGEND = '''    <sc-if value="{{legend}}" hint-placeholder-val="{{ true }}">
      <div aria-hidden="true" style="position: absolute; top: 0; bottom: 0; left: 46px; right: 0; pointer-events: none; background: linear-gradient(to right, rgba(248, 251, 252, 0.97) 0%, rgba(248, 251, 252, 0.95) 50%, rgba(248, 251, 252, 0.6) 72%, rgba(248, 251, 252, 0) 92%)"></div>
      <div class="lg-layer" onClick="{{hideLegend}}" style="position: absolute; top: 8px; bottom: 12px; left: 46px; right: 16px; cursor: pointer">
        <sc-for list="{{spans}}" as="sp" hint-placeholder-count="2">
          <div style="position: absolute; left: 2px; width: 7px; top: {{sp.top}}; height: {{sp.height}}; min-height: 4px; box-sizing: border-box; border: 2px solid {{sp.color}}; border-left: 0; border-radius: 0 4px 4px 0"></div>
        </sc-for>
        <sc-for list="{{items}}" as="it" hint-placeholder-count="5">
          <div style="position: absolute; left: 0; right: 0; top: {{it.top}}; height: 0">
            <div style="position: absolute; left: {{it.lineLeft}}; width: {{it.lineWidth}}; top: -1px; height: 2px; background: {{it.color}}"></div>
            <sc-if value="{{it.arrow}}" hint-placeholder-val="{{ true }}">
              <div style="position: absolute; left: 0; top: -5px; width: 0; height: 0; border-top: 5px solid transparent; border-bottom: 5px solid transparent; border-right: 7px solid {{it.color}}"></div>
            </sc-if>
            <div style="position: absolute; left: 30px; top: 0; transform: translateY(-50%); display: flex; align-items: baseline; gap: 8px; padding: {{it.pad}}; border-radius: 12px; background: {{it.bg}}; color: {{it.fg}}; white-space: nowrap">
              <span style="font-size: 15px; font-weight: 500">{{it.label}}</span>
              <span style="font-size: {{it.valueSize}}; font-weight: 500; color: {{it.valueColor}}">{{it.value}}</span>
            </div>
            <sc-if value="{{it.hasHint}}" hint-placeholder-val="{{ false }}">
              <div style="position: absolute; left: 31px; top: 13px; font-size: 12px; color: #40484B; white-space: nowrap">{{it.hint}}</div>
            </sc-if>
            <sc-if value="{{it.handle}}" hint-placeholder-val="{{ false }}">
              <div onPointerDown="{{it.dragStart}}" onClick="{{stop}}" title="{{it.hint}}" style="position: absolute; left: 0; top: -18px; width: 210px; height: 50px; cursor: ns-resize; touch-action: none"></div>
              <div style="position: absolute; left: 216px; top: -12px; display: flex; gap: 4px" onClick="{{stop}}">
                <button onClick="{{it.up}}" aria-label="{{simUp}}" style="width: 28px; height: 24px; border: 1px dashed #B9C2C5; border-radius: 8px; background: #FFFFFF; color: #70787B; font: 12px Roboto, sans-serif; cursor: pointer">&#9650;</button>
                <button onClick="{{it.down}}" aria-label="{{simDown}}" style="width: 28px; height: 24px; border: 1px dashed #B9C2C5; border-radius: 8px; background: #FFFFFF; color: #70787B; font: 12px Roboto, sans-serif; cursor: pointer">&#9660;</button>
              </div>
            </sc-if>
          </div>
        </sc-for>
        <p style="position: absolute; left: 30px; width: 200px; bottom: 26px; margin: 0; font-size: 12px; color: #70787B">{{closeHint}}</p>
        <sc-if value="{{nowTop3}}" hint-placeholder-val="{{ false }}">
          <button onClick="{{advance}}" style="position: absolute; left: 30px; bottom: 0; border: 1px dashed #B9C2C5; border-radius: 8px; background: #FFFFFF; padding: 2px 8px; color: #70787B; font: italic 11px Roboto, sans-serif; cursor: pointer">{{advanceLabel}}</button>
        </sc-if>
        <button onClick="{{toggleLang}}" style="position: absolute; left: 150px; bottom: 0; border: 1px dashed #B9C2C5; border-radius: 8px; background: #FFFFFF; padding: 2px 8px; color: #70787B; font: italic 11px Roboto, sans-serif; cursor: pointer">{{langLabel}}</button>
      </div>
    </sc-if>
'''

SCRIPT = '''<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":390,"height":844}}'>
const T = {
  it: { day: 'Pisolini', night: 'Sonno notturno', now: 'Adesso', nap: 'Pisolino', sleep: 'Sonno notturno',
        start: 'Inizio della notte', end: 'Fine della notte', drag: 'Trascina o tocca per cambiarlo',
        close: 'Tocca per chiudere. Tocca la linea del tempo per rivederlo.', lang: 'English',
        up: 'Simula: trascina in su', down: 'Simula: trascina in giù', tl: 'Linea del tempo, tocca per la legenda',
        statusDay: 'Nessuna sveglia per il pisolino', detailDay: 'I pisolini non partono da soli',
        statusNight: 'Nessuna sveglia notturna', capDay: 'Avvia il pisolino con il pulsante qui sotto',
        capNight: 'Attivala con il pulsante, solo per stanotte', manual: 'Manuale', auto: 'Automatico',
        startNap: 'Inizia pisolino', arm: 'Attiva stanotte', napped: 'Pisolini di oggi', include: 'Includi i pisolini di oggi',
        w1: 'Dopo %%s di sonno', w2: '%%s dopo essermi addormentato/a', w3d: '%%s da adesso', w3n: 'Alla fine della notte, alle %%s',
        window: 'Notte dalle %%1 alle %%2, %%3' },
  en: { day: 'Day naps', night: 'Night sleep', now: 'Now', nap: 'Nap', sleep: 'Night sleep',
        start: 'Night starts', end: 'Night ends', drag: 'Drag or tap to change it',
        close: 'Tap to close. Tap the timeline to see this again.', lang: 'Italiano',
        up: 'Simulate: drag up', down: 'Simulate: drag down', tl: 'Timeline, tap for the legend',
        statusDay: 'No nap alarm', detailDay: 'Naps don\\u2019t start by themselves',
        statusNight: 'No night alarm', capDay: 'Start a nap with the button below',
        capNight: 'Arm it with the button, for tonight only', manual: 'Manual', auto: 'Automatic',
        startNap: 'Start nap', arm: 'Arm tonight', napped: 'Napped today', include: 'Include today\\u2019s naps',
        w1: 'After %%s asleep', w2: '%%s after falling asleep', w3d: '%%s from now', w3n: 'At the night\\u2019s end, %%s',
        window: 'Night %%1 to %%2, %%3' },
};
const SHIFT = %(shift)s;
const NOWTOP = %(nowtop)s;
class Component extends DCLogic {
  constructor(props) {
    super(props);
    // Times in minutes after midnight
    this.state = SHIFT
      ? { tab: 'night', legend: true, drag: null, start: 450, end: 930, now: 1090, lang: 'it' }
      : { tab: 'day', legend: true, drag: null, start: 1365, end: 480, now: 845, lang: 'it' };
  }
  // The axis runs noon to noon, unless the night crosses noon: then midnight to midnight
  axisFor(start, end) {
    if (NOWTOP) return Math.floor(this.state.now / 60) * 60;
    return ((720 - start + 1440) %% 1440) < ((end - start + 1440) %% 1440) ? 0 : 720;
  }
  // A threshold moved: the window must hold the night's sleep and stay on the axis it started on
  tryMove(kind, value, axis) {
    const st = this.state;
    const start = kind === 'start' ? value : st.start, end = kind === 'end' ? value : st.end;
    const len = (end - start + 1440) %% 1440;
    if (len < 450 || len > 1440 - 120) return false;
    if (!NOWTOP) {
      const f = (m) => (m - axis + 1440) %% 1440;
      const e = f(end) || 1440;
      if (f(start) >= e) return false;
    }
    this.setState({ start, end });
    return true;
  }
  dragFrom(kind) {
    return (e) => {
      if (e.stopPropagation) e.stopPropagation();
      if (e.preventDefault) e.preventDefault();
      const row = e.currentTarget.closest('[data-row]');
      const height = row.querySelector('[data-track]').getBoundingClientRect().height;
      const y0 = e.clientY, m0 = this.state[kind];
      const axis = this.axisFor(this.state.start, this.state.end);
      this.setState({ drag: kind, legend: true });
      const move = (ev) => {
        const delta = Math.round((ev.clientY - y0) / height * 1440 / 15) * 15;
        this.tryMove(kind, (m0 + delta + 1440) %% 1440, axis);
      };
      const up = () => {
        document.removeEventListener('pointermove', move);
        document.removeEventListener('pointerup', up);
        this.justDragged = true;
        setTimeout(() => { this.justDragged = false; }, 50);
        this.setState({ drag: null });
      };
      document.addEventListener('pointermove', move);
      document.addEventListener('pointerup', up);
    };
  }
  step(kind, delta) {
    return (e) => {
      if (e && e.stopPropagation) e.stopPropagation();
      const st = this.state;
      if (this.tryMove(kind, (st[kind] + delta + 1440) %% 1440, this.axisFor(st.start, st.end))) {
        this.setState({ drag: kind });
        clearTimeout(this.t);
        this.t = setTimeout(() => this.setState({ drag: null }), 1600);
      }
    };
  }
  renderVals() {
    const s = this.state;
    const t = T[s.lang];
    const day = s.tab === 'day';
    const axis = this.axisFor(s.start, s.end);
    const pos = (m) => ((m - axis + 1440) %% 1440) / 1440;
    const pct = (f) => (f * 100).toFixed(2) + '%%';
    const hhmm = (m) => { m = ((m %% 1440) + 1440) %% 1440; return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m %% 60).padStart(2, '0'); };
    const dur = (m) => m < 60 ? m + ' min' : (m %% 60 ? Math.floor(m / 60) + ' h ' + (m %% 60) + ' min' : (m / 60) + ' h');
    const sF = pos(s.start), eF = pos(s.end) || 1;
    const nightLen = (s.end - s.start + 1440) %% 1440;
    const napLen = 20, sleepLen = 450;
    const clampTop = (f, len) => NOWTOP ? f : Math.min(Math.max(f, 0), 1 - len);
    const napTop = clampTop(pos(s.end + (1440 - nightLen) / 2 - napLen / 2), napLen / 1440);
    const sleepTop = clampTop(pos(s.start + nightLen / 2 - sleepLen / 2), sleepLen / 1440);
    const napH = napLen / 1440, sleepH = sleepLen / 1440;
    // Outside the legend each pane shows its own sleep; the legend shows both
    const showNap = s.legend || day, showSleep = s.legend || !day;

    const b = 60 / 1440;
    const dayC = day ? '#BFE9F3' : '#EAF6F9', nightC = day ? '#8FB3C2' : '#173A4F';
    const wraps = eF < sF;
    const stops = wraps
      ? [[nightC, 0], [nightC, Math.max(0, eF - b)], [dayC, Math.min(eF + b, sF)], [dayC, Math.max(sF - b, eF)], [nightC, Math.min(sF + b, 1)], [nightC, 1]]
      : [[dayC, 0], [dayC, Math.max(0, sF - b)], [nightC, Math.min(sF + b, eF)], [nightC, Math.max(eF - b, sF)], [dayC, Math.min(eF + b, 1)], [dayC, 1]];
    const tlGradient = 'linear-gradient(to bottom, ' + stops.map(([c, f]) => c + ' ' + pct(f)).join(', ') + ')';
    const inNight = (f) => wraps ? (f < eF || f > sF) : (f > sF && f < eF);
    const labelColor = (f) => (!day && inNight(f) && !(showSleep && f >= sleepTop && f <= sleepTop + sleepH)) ? '#FFFFFF' : '#191C1D';
    let hours;
    if (NOWTOP) {
      hours = [0, 8].map((i) => ({ label: String((axis / 60) %% 24).padStart(2, '0'), top: i ? 'calc(100%% - 7px)' : '7px', color: labelColor(i / 8) }));
      for (let h = 0; h < 24; h += 3) {
        const f = pos(h * 60);
        if (f < 0.06 || f > 0.94) continue;
        hours.push({ label: String(h).padStart(2, '0'), top: pct(f), color: labelColor(f) });
      }
    } else {
      hours = [0,1,2,3,4,5,6,7,8].map((i) => ({
        label: String((axis / 60 + i * 3) %% 24).padStart(2, '0'),
        top: i === 0 ? '7px' : i === 8 ? 'calc(100%% - 7px)' : pct(i / 8), color: labelColor(i / 8),
      }));
    }
    const at = (m) => { const f = pos(m); return f === 0 ? ['7px', 'calc(100%% - 7px)'] : [pct(f)]; };
    const suns = at(720).map((top) => ({ top }));
    const moons = at(0).map((top) => ({ top }));

    const deep = '#1F7A91';
    const item = (kind, f, label, value, extra) => {
      const dragging = s.drag === kind;
      const dim = s.drag && !dragging;
      return Object.assign({
        top: pct(f), label, value, arrow: true, lineLeft: '6px', lineWidth: '22px', handle: false,
        color: dim ? '#B9C2C5' : deep, bg: dragging ? deep : 'transparent', fg: dragging ? '#FFFFFF' : (dim ? '#8A9599' : '#191C1D'),
        valueColor: dragging ? '#FFFFFF' : (dim ? '#8A9599' : deep), valueSize: dragging ? '20px' : '15px',
        pad: dragging ? '4px 10px' : '0', hasHint: false, hint: '',
      }, extra || {});
    };
    const spanItem = (top, h, label, value) => item('span', top + h / 2, label, value, { arrow: false, lineLeft: '9px', lineWidth: '19px' });
    const handle = (kind, f, label, time) => item(kind, f, label, time, {
      hasHint: !s.drag, hint: t.drag, handle: true, dragStart: this.dragFrom(kind),
      up: this.step(kind, -15), down: this.step(kind, 15),
    });
    const items = [
      item('now', pos(s.now), t.now, hhmm(s.now)),
      spanItem(napTop, napH, t.nap, dur(napLen)),
      handle('start', sF, t.start, hhmm(s.start)),
      spanItem(sleepTop, sleepH, t.sleep, dur(sleepLen)),
      handle('end', eF, t.end, hhmm(s.end)),
    ];
    const spanColor = s.drag ? '#B9C2C5' : deep;
    const spans = [{ top: pct(napTop), height: pct(napH), color: spanColor }, { top: pct(sleepTop), height: pct(sleepH), color: spanColor }];

    const halo = (k) => s.drag === k ? '0 0 0 6px rgba(31, 122, 145, 0.25)' : 'none';
    const amount = day ? dur(napLen) : dur(sleepLen);
    const fill = (str, a) => str.replace('%%s', a);
    const advance = (e) => { if (e && e.stopPropagation) e.stopPropagation(); this.setState({ now: (this.state.now + 180) %% 1440 }); };
    return {
      isDay: day, isNight: !day,
      title: day ? t.day : t.night,
      legend: s.legend, items, spans, closeHint: s.drag ? '' : t.close, langLabel: t.lang,
      nowTop3: NOWTOP, advance, advanceLabel: s.lang === 'it' ? 'simula: 3 h dopo' : 'simulate: 3 h later',
      simUp: t.up, simDown: t.down, tlLabel: t.tl,
      showLegend: () => this.setState({ legend: true }),
      hideLegend: () => { if (!this.justDragged) this.setState({ legend: false, drag: null }); },
      stop: (e) => { if (e && e.stopPropagation) e.stopPropagation(); },
      toggleLang: (e) => { if (e && e.stopPropagation) e.stopPropagation(); this.setState({ lang: s.lang === 'it' ? 'en' : 'it' }); },
      dragStartStart: this.dragFrom('start'), dragStartEnd: this.dragFrom('end'),
      tlGradient, hours, suns, moons,
      showNap, showSleep,
      napTop: pct(napTop), napHeight: pct(napH), napColor: deep,
      sleepTop: pct(sleepTop), sleepHeight: pct(sleepH), sleepColor: day ? deep : '#9EDDEE',
      startTop: pct(sF), endTop: pct(eF), startHalo: halo('start'), endHalo: halo('end'),
      statusTitle: day ? t.statusDay : t.statusNight,
      statusDetail: day ? t.detailDay : t.window.replace('%%1', hhmm(s.start)).replace('%%2', hhmm(s.end)).replace('%%3', dur(nightLen)),
      cardBg: '#E8F1F4', cardFg: '#191C1D', cardSoft: '#40484B',
      onceLabel: t.manual, everyLabel: t.auto, onceBg: '#CDEFF8', everyBg: 'transparent',
      setOnce: () => {}, setEvery: () => {},
      modeCaption: day ? t.capDay : t.capNight,
      amount, amountPct: day ? '9%%' : '60%%',
      wake1: fill(t.w1, amount), wake2: fill(t.w2, amount), wake3: day ? fill(t.w3d, amount) : fill(t.w3n, hhmm(s.end)),
      extraLabel: day ? t.napped : t.include,
      rbClass: 'rb rb-idle', rbLabel: day ? t.startNap : t.arm, press: () => {}, hasSim: false, simLabel: '', sim: () => {},
      hasToast: false, toast: '',
      showDay: (e) => { if (e) e.preventDefault(); this.setState({ tab: 'day', drag: null }); },
      showNight: (e) => { if (e) e.preventDefault(); this.setState({ tab: 'night', drag: null }); },
      dayPill: day ? '#CDEFF8' : 'transparent', nightPill: day ? 'transparent' : '#CDEFF8',
      dayWeight: day ? '700' : '400', nightWeight: day ? '400' : '700',
      chipBg: '#D6F0DC', chipFg: '#1E5631', chipLabel: 'Amazfit Bip',
    };
  }
}
</script>
</body>
</html>
'''


def page(title, shift, nowtop=False):
    content = STATUS + TOGGLE + REST + SPACER + BUTTON + SPACER
    return (HEAD.format(title=title) +
            '<div style="width: 390px; height: 844px; box-sizing: border-box; background: #F8FBFC; display: flex; flex-direction: column; position: relative">\n' +
            HEADER +
            '  <div data-row="1" style="flex: 1; min-height: 0; display: flex; gap: 10px; padding: 4px 20px 8px 10px; position: relative">\n' +
            TIMELINE +
            '      <main style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px">\n' +
            content +
            '      </main>\n' + LEGEND + '  </div>\n' + NAV + '</div>\n</x-dc>\n' +
            SCRIPT % {'shift': 'true' if shift else 'false', 'nowtop': 'true' if nowtop else 'false'})


open(out_dir + '/TimelineLegend.dc.html', 'w').write(page('WakeBridge, legenda della linea del tempo', False))
open(out_dir + '/TimelineShift.dc.html', 'w').write(page('WakeBridge, turno di notte', True))
open(out_dir + '/TimelineNowTop.dc.html', 'w').write(page('WakeBridge, adesso in cima', False, True))
print('ok')
