"use client";

import { CircleCheck, Compass, Plus, Sparkles, TriangleAlert } from "lucide-react";

export type NavigatorStatus = "ON_TRACK" | "WATCH" | "OVER_BUDGET" | "NEEDS_DATA";
export type ForecastSource = "CURRENT_PACE" | "HISTORICAL_AVERAGE" | "NO_DATA";

export type SpendingNavigatorData = {
  month: string;
  income: number;
  expense: number;
  budget: number;
  limitAmount: number;
  limitLabel: string;
  remainingBase: number;
  safeDaily: number;
  projectedExpense: number;
  projectedBalance: number;
  remainingDays: number;
  elapsedDays: number;
  pacePercent: number;
  status: NavigatorStatus;
  message: string;
  confidence: "HIGH" | "MEDIUM" | "LOW";
  historicalMonths: number;
  forecastSource: ForecastSource;
  historicalAverageExpense: number;
};

type SpendingNavigatorProps = {
  data: SpendingNavigatorData;
  plannedRecurringTotal?: number;
  scenarioAmount: number;
  onScenarioChange: (amount: number) => void;
  onOpenBudget: () => void;
  onOpenTransaction: () => void;
};

const statusLabels: Record<NavigatorStatus, string> = {
  ON_TRACK: "안정권",
  WATCH: "주의 필요",
  OVER_BUDGET: "기준 초과",
  NEEDS_DATA: "기록이 필요해요",
};

const forecastSourceLabels: Record<ForecastSource, string> = {
  CURRENT_PACE: "이번 달 지출 속도",
  HISTORICAL_AVERAGE: "최근 기록 평균",
  NO_DATA: "기록 대기 중",
};

const confidenceLabels = { HIGH: "높음", MEDIUM: "보통", LOW: "낮음" };
const presetAmounts = [50000, 100000, 300000];
const money = (value: number) => `${value.toLocaleString("ko-KR")}원`;

export function SpendingNavigator({ data, plannedRecurringTotal = 0, scenarioAmount, onScenarioChange, onOpenBudget, onOpenTransaction }: SpendingNavigatorProps) {
  const scenarioRemainingBase = Math.max(0, data.remainingBase - scenarioAmount);
  const scenarioDaily = Math.floor(scenarioRemainingBase / Math.max(1, data.remainingDays));
  const scenarioBalance = data.projectedBalance - scenarioAmount;
  const remainingSpend = Math.max(0, data.limitAmount - data.expense);
  const todayLabel = new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short" }).format(new Date());
  const needsData = data.status === "NEEDS_DATA";

  return (
    <section className={`navigator-panel navigator-core status-${data.status.toLowerCase()}`} id="navigator" aria-labelledby="navigator-title">
      <div className="navigator-main">
        <div className="navigator-hero-head">
          <div>
            <span className="eyebrow"><Compass size={12} /> 생활비 내비게이터</span>
            <h2 id="navigator-title">오늘의 생활비 결정</h2>
          </div>
          <div className="navigator-head-meta">
            <span suppressHydrationWarning>{todayLabel}</span>
            <span className={`navigator-status ${data.status.toLowerCase()}`}>
              {data.status === "ON_TRACK" ? <CircleCheck size={13} /> : needsData ? <Sparkles size={13} /> : <TriangleAlert size={13} />}
              {statusLabels[data.status]}
            </span>
          </div>
        </div>

        <div className="navigator-answer">
          <p>오늘 마음 놓고 쓸 수 있는 금액</p>
          <div className="navigator-value">
            <strong>{data.limitAmount > 0 ? money(data.safeDaily) : "계산 준비 중"}</strong>
            <span>{data.limitAmount > 0 ? `하루 기준 · ${data.remainingDays === 1 ? "오늘까지" : `${data.remainingDays}일 남음`}` : "기준액이 필요해요"}</span>
          </div>
          <p className="navigator-message">{data.message}</p>
          <div className="navigator-actions">
            <button className="navigator-primary-action" onClick={needsData ? onOpenBudget : onOpenTransaction}><Plus size={15} /> {needsData ? "예산부터 정하기" : "오늘 거래 기록"}</button>
            <button className="navigator-secondary-action" onClick={needsData ? onOpenTransaction : onOpenBudget}>{needsData ? "수입 먼저 기록" : "예산 조정"}</button>
          </div>
        </div>

        <div className="navigator-proof">
          <div className="navigator-progress-copy">
            <span>이번 달 기준액 사용</span>
            <b>{data.limitAmount > 0 ? `${data.pacePercent}%` : "기록 대기"}</b>
          </div>
          <div className="navigator-meter" aria-label={data.limitAmount > 0 ? `기준액의 ${data.pacePercent}% 사용` : "예산 또는 수입 기록 필요"}>
            <div><span style={{ width: `${Math.min(100, data.pacePercent)}%` }} /></div>
          </div>
          <div className="navigator-stats">
            <div><small>이번 달 남은 기준액</small><b>{data.limitAmount > 0 ? money(remainingSpend) : "기록 필요"}</b></div>
            <div><small>월말 예상 지출</small><b>{money(data.projectedExpense)}</b></div>
            <div><small>월말 예상 여유</small><b className={data.projectedBalance < 0 ? "negative" : ""}>{data.limitAmount > 0 ? `${data.projectedBalance < 0 ? "−" : ""}${money(Math.abs(data.projectedBalance))}` : "계산 대기"}</b></div>
          </div>
          {plannedRecurringTotal > 0 && (
            <div className="navigator-commitment">
              <span>고정비 예정</span>
              <b>{money(plannedRecurringTotal)}</b>
              <small>고정비 루틴에 저장한 월 계획 · 오늘 한도와 분리해 표시</small>
            </div>
          )}
          <details className="navigator-formula">
            <summary>이 금액은 어떻게 계산했나요?</summary>
            <p>{data.limitLabel} {data.limitAmount > 0 ? money(data.limitAmount) : "미설정"}에서 이번 달 지출 {money(data.expense)}을 빼고 남은 기간으로 나눴어요.</p>
            <div>
              <span>{forecastSourceLabels[data.forecastSource]}</span>
              <span>예측 신뢰도 {confidenceLabels[data.confidence]}</span>
              <span>참고용 예측 · 금융 조언 아님</span>
            </div>
          </details>
        </div>
      </div>

      <div className="scenario-box">
        <div className="scenario-head">
          <span><Sparkles size={14} /> 큰 지출 미리보기</span>
          <small>내 기록에는 저장되지 않아요</small>
        </div>
        <label htmlFor="scenario-amount">추가로 쓸 예정 금액</label>
        <div className="scenario-presets" aria-label="가상 지출 빠른 선택">
          {presetAmounts.map((amount) => (
            <button key={amount} className={scenarioAmount === amount ? "active" : ""} onClick={() => onScenarioChange(amount)}>{amount / 10000}만원</button>
          ))}
          {scenarioAmount > 0 ? <button onClick={() => onScenarioChange(0)}>초기화</button> : null}
        </div>
        <input
          id="scenario-amount"
          className="scenario-range"
          type="range"
          min="0"
          max={Math.max(1_000_000, data.limitAmount)}
          step={10000}
          value={scenarioAmount}
          onChange={(event) => onScenarioChange(Number(event.target.value))}
        />
        <div className="scenario-input-row">
          <b>{money(scenarioAmount)}</b>
          <label><span>직접 입력</span><input aria-label="가상 지출 금액" type="number" min="0" max="10000000" step="10000" value={scenarioAmount} onChange={(event) => {
            const amount = Number(event.target.value);
            onScenarioChange(Number.isFinite(amount) ? Math.min(10_000_000, Math.max(0, amount)) : 0);
          }} /></label>
        </div>
        <div className={`scenario-result ${scenarioBalance < 0 ? "danger" : ""}`} aria-live="polite">
          {data.limitAmount > 0
            ? scenarioAmount > 0
              ? <><b>{scenarioBalance >= 0 ? "월말에도 " : "월말에 "}{money(Math.abs(scenarioBalance))}{scenarioBalance >= 0 ? " 정도 남아요" : "가 부족해져요"}</b><span>이 지출 후 하루 안심 사용액은 {money(scenarioDaily)}입니다.</span></>
              : <><b>예정된 큰 지출이 있나요?</b><span>금액을 넣으면 월말 여유와 하루 한도가 바로 바뀝니다.</span></>
            : <><b>예산 또는 수입을 먼저 기록해 주세요.</b><span>기준액이 생기면 큰 지출의 영향을 미리 볼 수 있어요.</span></>}
        </div>
      </div>
    </section>
  );
}
