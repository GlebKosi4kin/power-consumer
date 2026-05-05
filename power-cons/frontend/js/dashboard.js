'use strict';

const API = 'http://localhost:9000/api';

const PERIOD_LABELS = {
  T1_NIGHT:   'T1 Ночь',
  T2_MORNING: 'T2 Утро',
  T3_DAY:     'T3 День',
  T2_EVENING: 'T2 Вечер',
};

const PERIOD_COLORS = {
  T1_NIGHT:   'rgba(126,166,247,0.85)',
  T2_MORNING: 'rgba(247,196,79,0.85)',
  T3_DAY:     'rgba(224,82,82,0.85)',
  T2_EVENING: 'rgba(247,162,79,0.85)',
};

// ── chart instances ───────────────────────────────────────────────────────────
let dailyChart   = null;
let monthlyChart = null;

// ── helpers ───────────────────────────────────────────────────────────────────
async function apiFetch(path) {
  const res = await fetch(API + path);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

function fmt(n, digits = 1) {
  return Number(n).toFixed(digits);
}

// ── stats cards ───────────────────────────────────────────────────────────────
async function loadStats(date) {
  try {
    const s = await apiFetch(`/stats/daily?date=${date}`);
    document.getElementById('stat-avg').textContent       = fmt(s.avgMwh);
    document.getElementById('stat-peak').textContent      = fmt(s.peakMwh);
    document.getElementById('stat-total').textContent     = fmt(s.totalMwh, 0);
    document.getElementById('stat-anomalies').textContent = s.anomalyCount;
    document.getElementById('stat-anomalies').style.color =
      s.anomalyCount > 0 ? 'var(--danger)' : 'var(--ok)';
  } catch {
    ['stat-avg','stat-peak','stat-total','stat-anomalies']
      .forEach(id => { document.getElementById(id).textContent = '—'; });
  }
}

// ── daily chart ───────────────────────────────────────────────────────────────
async function loadDailyChart(date) {
  const readings = await apiFetch(`/readings?date=${date}`);

  const labels  = readings.map(r => `${String(r.hour).padStart(2,'0')}:00`);
  const values  = readings.map(r => r.consumptionMwh);
  const bgColors = readings.map(r =>
    r.isAnomaly ? 'rgba(224,82,82,0.9)' : PERIOD_COLORS[r.period] ?? 'rgba(79,142,247,0.75)'
  );
  const borderColors = readings.map(r =>
    r.isAnomaly ? '#e05252' : (PERIOD_COLORS[r.period] ?? '#4f8ef7')
  );

  if (dailyChart) dailyChart.destroy();

  const ctx = document.getElementById('chart-daily').getContext('2d');
  dailyChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'МВт·ч',
        data: values,
        backgroundColor: bgColors,
        borderColor: borderColors,
        borderWidth: 1,
        borderRadius: 4,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (items) => `${items[0].label} — ${PERIOD_LABELS[readings[items[0].dataIndex]?.period] ?? ''}`,
            label: (item)  => ` ${fmt(item.raw)} МВт·ч` +
              (readings[item.dataIndex]?.isAnomaly ? '  ⚠ АНОМАЛИЯ' : ''),
          },
        },
      },
      scales: {
        x: { ticks: { color: '#888ba0' }, grid: { color: '#2a2d3a' } },
        y: {
          ticks: { color: '#888ba0' },
          grid:  { color: '#2a2d3a' },
          title: { display: true, text: 'МВт·ч', color: '#888ba0' },
        },
      },
    },
  });
}

// ── monthly chart ─────────────────────────────────────────────────────────────
async function loadMonthlyChart() {
  const stats = await apiFetch('/stats/monthly?year=2026&month=1');

  const labels     = stats.map(s => s.date.slice(5)); // "01-15"
  const totals     = stats.map(s => s.totalMwh);
  const peaks      = stats.map(s => s.peakMwh);
  const anomalies  = stats.map(s => s.anomalyCount);

  if (monthlyChart) monthlyChart.destroy();

  const ctx = document.getElementById('chart-monthly').getContext('2d');
  monthlyChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'Итого МВт·ч',
          data: totals,
          borderColor: '#4f8ef7',
          backgroundColor: 'rgba(79,142,247,0.12)',
          fill: true,
          tension: 0.35,
          pointRadius: anomalies.map(a => a > 0 ? 6 : 3),
          pointBackgroundColor: anomalies.map(a => a > 0 ? '#e05252' : '#4f8ef7'),
        },
        {
          label: 'Пик МВт·ч',
          data: peaks,
          borderColor: '#f7a24f',
          backgroundColor: 'transparent',
          borderDash: [5,4],
          tension: 0.35,
          pointRadius: 2,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          labels: { color: '#e8eaf0' },
        },
        tooltip: {
          callbacks: {
            afterLabel: (item) => {
              const a = anomalies[item.dataIndex];
              return a > 0 ? `  ⚠ аномалий: ${a}` : null;
            },
          },
        },
      },
      scales: {
        x: { ticks: { color: '#888ba0' }, grid: { color: '#2a2d3a' } },
        y: {
          ticks: { color: '#888ba0' },
          grid:  { color: '#2a2d3a' },
          title: { display: true, text: 'МВт·ч', color: '#888ba0' },
        },
      },
    },
  });
}

// ── anomalies panel ───────────────────────────────────────────────────────────
async function loadAnomalies(from, to) {
  const items = await apiFetch(`/anomalies?from=${from}&to=${to}`);
  const el = document.getElementById('anomaly-list');

  if (!items.length) {
    el.innerHTML = '<p class="empty-msg">Аномалий не обнаружено.</p>';
    return;
  }

  el.innerHTML = items.map(r => `
    <div class="anomaly-item">
      <span class="anomaly-badge">${r.date} ${String(r.hour).padStart(2,'0')}:00</span>
      <span class="anomaly-period ${r.period}">${PERIOD_LABELS[r.period] ?? r.period} — ${fmt(r.consumptionMwh)} МВт·ч</span>
      <span class="anomaly-rec">${r.recommendation ?? ''}</span>
    </div>
  `).join('');
}

// ── view switching ────────────────────────────────────────────────────────────
function showDailyView() {
  document.getElementById('section-daily').style.display   = '';
  document.getElementById('section-monthly').style.display = 'none';
}

function showMonthlyView() {
  document.getElementById('section-daily').style.display   = 'none';
  document.getElementById('section-monthly').style.display = '';
}

// ── init ──────────────────────────────────────────────────────────────────────
async function loadDay(date) {
  showDailyView();
  await Promise.all([
    loadStats(date),
    loadDailyChart(date),
    loadAnomalies(date, date),
  ]);
}

async function loadMonth() {
  showMonthlyView();
  await Promise.all([
    loadMonthlyChart(),
    loadAnomalies('2026-01-01', '2026-02-01'),
  ]);
  // reset stat cards to monthly totals
  const stats = await apiFetch('/stats/monthly?year=2026&month=1');
  if (stats.length) {
    const avg  = stats.reduce((s,r) => s + Number(r.avgMwh),   0) / stats.length;
    const peak = Math.max(...stats.map(r => Number(r.peakMwh)));
    const tot  = stats.reduce((s,r) => s + Number(r.totalMwh), 0);
    const anom = stats.reduce((s,r) => s + r.anomalyCount,     0);
    document.getElementById('stat-avg').textContent       = fmt(avg);
    document.getElementById('stat-peak').textContent      = fmt(peak);
    document.getElementById('stat-total').textContent     = fmt(tot, 0);
    document.getElementById('stat-anomalies').textContent = anom;
    document.getElementById('stat-anomalies').style.color =
      anom > 0 ? 'var(--danger)' : 'var(--ok)';
  }
}

document.getElementById('btn-load-day').addEventListener('click', () => {
  const date = document.getElementById('date-picker').value;
  if (date) loadDay(date);
});

document.getElementById('btn-load-month').addEventListener('click', loadMonth);

// initial load
loadDay(document.getElementById('date-picker').value);
