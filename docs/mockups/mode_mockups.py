"""Interactive Italian mockups of the Day/Night mode control: a Manuale/Automatico toggle
(the round button greyed while automatic) and a switch (the button always usable)."""
import sys

out_dir = sys.argv[1]
src = open(__file__.replace('mode_mockups.py', 'stable_mockups.py')).read()
ns = {'sys': sys}
sys_argv, sys.argv = sys.argv, ['x', '/nonexistent']
exec(src[:src.index("SPACER = ")], ns)
sys.argv = sys_argv
HEAD, TIMELINE, STATUS, NAV, CONTROLS = ns['HEAD'], ns['TIMELINE'], ns['STATUS'], ns['NAV'], ns['CONTROLS']

HEAD = HEAD.replace('<html lang="en">', '<html lang="it">')
NAV = NAV.replace('      Day naps\n', '      Pisolini\n').replace('      Night sleep\n', '      Sonno notturno\n') \
    .replace('      Settings\n', '      Impostazioni\n').replace('aria-label="Sections"', 'aria-label="Sezioni"')
CONTROLS = CONTROLS.replace('>Sleep amount<', '>Quantità di sonno<').replace('>Wake me<', '>Svegliami<') \
    .replace('aria-label="Sleep amount"', 'aria-label="Quantità di sonno"') \
    .replace('aria-label="Include today\'s naps"', 'aria-label="Includi i pisolini di oggi"') \
    .replace('aria-label="Mode"', 'aria-label="Modalità"')

toggle_start = CONTROLS.index('        <div role="group"')
toggle_end = CONTROLS.index('        <div style="flex: none; display: flex; flex-direction: column; gap: 2px">')
TOGGLE = CONTROLS[toggle_start:toggle_end]
REST = CONTROLS[toggle_end:]

SWITCH = '''        <div style="flex: none; height: 40px; display: flex; align-items: center; justify-content: space-between; gap: 12px">
          <span style="font-size: 15px; font-weight: 500">{{switchLabel}}</span>
          <button role="switch" aria-checked="{{everyStr}}" aria-label="{{switchLabel}}" onClick="{{toggleEvery}}" style="flex: none; width: 52px; height: 32px; box-sizing: border-box; border-radius: 16px; border: 2px solid {{swBorder}}; background: {{swTrack}}; position: relative; padding: 0; cursor: pointer">
            <span style="position: absolute; left: {{swKnobLeft}}; top: {{swKnobTop}}; width: {{swKnob}}; height: {{swKnob}}; border-radius: 12px; background: {{swKnobColor}}"></span>
          </button>
        </div>
        <p style="flex: none; margin: -6px 4px 0; height: 16px; font-size: 12px; color: #40484B; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{{modeCaption}}</p>

'''

BUTTON = '''        <div style="flex: none; height: 160px; display: flex; align-items: center; justify-content: center; position: relative">
          <button class="{{rbClass}}" onClick="{{press}}" aria-label="{{rbLabel}}">
            <span class="glow"></span>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"></path></svg>
            <span>{{rbLabel}}</span>
          </button>
          <sc-if value="{{hasSim}}" hint-placeholder-val="{{ false }}">
            <button onClick="{{sim}}" style="position: absolute; bottom: -10px; border: 1px dashed #B9C2C5; border-radius: 8px; background: transparent; padding: 2px 8px; color: #70787B; font: italic 11px Roboto, sans-serif; cursor: pointer">{{simLabel}}</button>
          </sc-if>
        </div>
'''

TOAST = '''  <sc-if value="{{hasToast}}" hint-placeholder-val="{{ false }}">
    <div role="status" style="position: absolute; left: 24px; right: 24px; bottom: 104px; padding: 12px 16px; border-radius: 22px; background: #2E3132; color: #F0F1F1; font-size: 14px; text-align: center">{{toast}}</div>
  </sc-if>
'''

SCRIPT = '''<script type="text/x-dc" data-dc-script data-props='{"$preview":{"width":390,"height":844}}'>
class Component extends DCLogic {
  constructor(props) {
    super(props);
    this.state = { tab: 'day', dayAuto: false, nightAuto: false, napRunning: false, armed: false, nightStarted: false, skipped: false, toast: '' };
  }
  say(text) {
    this.setState({ toast: text });
    clearTimeout(this.t);
    this.t = setTimeout(() => this.setState({ toast: '' }), 3500);
  }
  renderVals() {
    const s = this.state;
    const SWITCH = %(switch)s;
    const day = s.tab === 'day';
    const auto = day ? s.dayAuto : s.nightAuto;
    const sel = '#CDEFF8';
    const win = 'Notte dalle 22:45 alle 08:00, 9 h 15 min';
    let statusTitle, statusDetail, rbClass = 'rb rb-idle', rbLabel, press = () => {}, hasSim = false, simLabel = '', sim = () => {};
    if (day) {
      if (s.napRunning) {
        statusTitle = auto ? 'Sonno in corso' : 'Ancora sveglio/a';
        statusDetail = auto ? 'Suona alle 14:25, dormito 2 min su 20' : 'Suona alle 14:20, pisolino di 20 min';
        rbClass = 'rb rb-run'; rbLabel = 'Ferma pisolino';
        press = () => this.setState({ napRunning: false });
      } else if (auto && !SWITCH) {
        statusTitle = 'In attesa di un pisolino';
        statusDetail = 'Pisolini dalle 08:00 alle 22:45';
        rbClass = 'rb rb-off'; rbLabel = 'In ascolto';
        press = () => this.say('Il pisolino parte da solo quando ti addormenti. Per avviarlo ora, scegli Manuale.');
        hasSim = true; simLabel = 'simula: ti addormenti'; sim = () => this.setState({ napRunning: true });
      } else {
        statusTitle = auto ? 'In attesa di un pisolino' : 'Nessuna sveglia per il pisolino';
        statusDetail = auto ? 'Pisolini dalle 08:00 alle 22:45' : 'I pisolini non partono da soli';
        rbLabel = 'Inizia pisolino';
        press = () => this.setState({ napRunning: true });
        if (auto) { hasSim = true; simLabel = 'simula: ti addormenti'; sim = () => this.setState({ napRunning: true }); }
      }
    } else if (auto) {
      statusDetail = win;
      if (s.nightStarted && !s.skipped) {
        statusTitle = 'Ancora sveglio/a';
        statusDetail = 'Suona alle 08:00, sonno di 7 h 30 min';
        rbClass = 'rb rb-run'; rbLabel = 'Salta stanotte';
        press = () => { this.setState({ skipped: true }); this.say('Saltata stanotte: si riattiva domani alle 22:45.'); };
      } else if (s.skipped) {
        statusTitle = 'Saltata stanotte';
        statusDetail = 'Si riattiva domani alle 22:45';
        rbClass = 'rb rb-off'; rbLabel = 'Dalle 22:45';
        press = () => this.say('Si riattiva da sola domani alle 22:45.');
        hasSim = true; simLabel = 'simula: domani'; sim = () => this.setState({ skipped: false, nightStarted: false });
      } else {
        statusTitle = 'Attiva ogni notte';
        rbClass = 'rb rb-off'; rbLabel = 'Dalle 22:45';
        press = () => this.say('Si attiva da sola alle 22:45. Da lì puoi saltare la notte.');
        hasSim = true; simLabel = 'simula: sono le 22:45'; sim = () => this.setState({ nightStarted: true });
      }
    } else {
      statusDetail = win;
      if (s.armed) {
        statusTitle = 'Attiva per stanotte';
        rbClass = 'rb rb-run'; rbLabel = 'Disattiva';
        press = () => this.setState({ armed: false });
      } else {
        statusTitle = 'Nessuna sveglia notturna';
        rbLabel = 'Attiva stanotte';
        press = () => this.setState({ armed: true });
      }
    }
    const dark = !day && ((s.nightAuto && !s.skipped) || s.armed);
    const hours = ['12','15','18','21','00','03','06','09','12'].map((label, i) => {
      const f = i / 8;
      const inNight = f > 0.448 && f < 0.833;
      return {
        label,
        top: i === 0 ? '7px' : i === 8 ? 'calc(100%% - 7px)' : (f * 100) + '%%',
        color: (!day && inNight && !(f > 0.47 && f < 0.80)) ? '#FFFFFF' : '#191C1D',
      };
    });
    const setAuto = (v) => this.setState(day ? { dayAuto: v, napRunning: false } : { nightAuto: v, armed: false, nightStarted: false, skipped: false });
    return {
      isDay: day, isNight: !day,
      title: day ? 'Pisolini' : 'Sonno notturno',
      statusTitle, statusDetail, rbClass, rbLabel, press, hasSim, simLabel, sim,
      hasToast: !!s.toast, toast: s.toast,
      cardBg: dark ? '#173A4F' : '#E8F1F4',
      cardFg: dark ? '#E3F4F8' : '#191C1D',
      cardSoft: dark ? '#BCDDE8' : '#40484B',
      onceLabel: 'Manuale',
      everyLabel: 'Automatico',
      onceBg: auto ? 'transparent' : sel,
      everyBg: auto ? sel : 'transparent',
      setOnce: () => setAuto(false),
      setEvery: () => setAuto(true),
      switchLabel: day ? 'Pisolini automatici' : 'Ogni notte',
      everyStr: auto ? 'true' : 'false',
      toggleEvery: () => setAuto(!auto),
      swBorder: auto ? '#1F7A91' : '#70787B',
      swTrack: auto ? '#1F7A91' : '#E1E8EB',
      swKnob: auto ? '24px' : '16px',
      swKnobLeft: auto ? '22px' : '6px',
      swKnobTop: auto ? '2px' : '6px',
      swKnobColor: auto ? '#FFFFFF' : '#70787B',
      modeCaption: day
        ? (SWITCH
            ? (auto ? 'Partono da soli quando ti addormenti, dalle 08:00 alle 22:45' : 'I pisolini partono solo con il pulsante')
            : (auto ? 'Il pisolino parte da solo quando ti addormenti' : 'Avvia il pisolino con il pulsante qui sotto'))
        : (auto ? 'Si attiva da sola ogni notte alle 22:45' : 'Attivala con il pulsante, solo per stanotte'),
      amount: day ? '20 min' : '7 h 30 min',
      amountPct: day ? '9%%' : '82%%',
      wake1: day ? 'Dopo 20 min di sonno' : 'Dopo 7 h 30 min di sonno',
      wake2: day ? '20 min dopo essermi addormentato/a' : '7 h 30 min dopo essermi addormentato/a',
      wake3: day ? '20 min da adesso' : 'Alla fine della notte, alle 08:00',
      extraLabel: day ? 'Pisolini di oggi' : 'Includi i pisolini di oggi',
      tlGradient: day
        ? 'linear-gradient(to bottom, #BFE9F3 0%%, #BFE9F3 40%%, #8FB3C2 49%%, #8FB3C2 79%%, #BFE9F3 88%%, #BFE9F3 100%%)'
        : 'linear-gradient(to bottom, #EAF6F9 0%%, #EAF6F9 40%%, #173A4F 49%%, #173A4F 79%%, #EAF6F9 88%%, #EAF6F9 100%%)',
      previewTop: day ? '9.7%%' : '48%%',
      previewHeight: day ? '1.4%%' : '31.2%%',
      previewColor: day ? '#1F7A91' : '#9EDDEE',
      hours,
      showDay: (e) => { if (e) e.preventDefault(); this.setState({ tab: 'day', toast: '' }); },
      showNight: (e) => { if (e) e.preventDefault(); this.setState({ tab: 'night', toast: '' }); },
      dayPill: day ? sel : 'transparent', nightPill: day ? 'transparent' : sel,
      dayWeight: day ? '700' : '400', nightWeight: day ? '400' : '700',
      connected: true, disconnected: false,
      chipBg: '#D6F0DC', chipFg: '#1E5631', chipLabel: 'Amazfit Bip, connesso',
      toggleConnected: () => {},
    };
  }
}
</script>
</body>
</html>
'''

HEADER = '''  <header style="flex: none; padding: 16px 16px 8px 20px; display: flex; align-items: center; justify-content: space-between">
    <h1 style="margin: 0; font-size: 22px; font-weight: 500">{{title}}</h1>
    <span aria-label="{{chipLabel}}" style="display: flex; align-items: center; gap: 4px; padding: 6px 12px 6px 8px; border-radius: 16px; background: {{chipBg}}; color: {{chipFg}}; font: 500 13px Roboto, sans-serif">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 7l10 10-5 5V2l5 5L7 17"></path></svg>
      Amazfit Bip
    </span>
  </header>
'''

SPACER = '        <div style="flex: 1; min-height: 8px"></div>\n'


def page(title, control, switch):
    content = STATUS + control + REST + SPACER + BUTTON + SPACER
    return (HEAD.format(title=title) +
            '<div style="width: 390px; height: 844px; box-sizing: border-box; background: #F8FBFC; display: flex; flex-direction: column; position: relative">\n' +
            HEADER +
            '  <div style="flex: 1; min-height: 0; display: flex; gap: 10px; padding: 4px 20px 8px 10px">\n' +
            TIMELINE +
            '      <main style="flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px">\n' +
            content +
            '      </main>\n  </div>\n' + TOAST + NAV + '</div>\n</x-dc>\n' +
            SCRIPT % {'switch': 'true' if switch else 'false'})


open(out_dir + '/ModeToggle.dc.html', 'w').write(page('WakeBridge, Manuale o Automatico', TOGGLE, False))
open(out_dir + '/ModeSwitch.dc.html', 'w').write(page('WakeBridge, interruttore automatico', SWITCH, True))
print('ok')
