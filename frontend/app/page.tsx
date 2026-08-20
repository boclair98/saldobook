"use client";

import {
  ArrowDownLeft,
  ArrowUpRight,
  Bell,
  ChevronRight,
  CircleHelp,
  CreditCard,
  Download,
  Landmark,
  LayoutGrid,
  Leaf,
  LoaderCircle,
  LockKeyhole,
  LogOut,
  Menu,
  Plus,
  PiggyBank,
  Search,
  Settings,
  ShieldCheck,
  Target,
  Trash2,
  TrendingUp,
  WalletCards,
  X,
} from "lucide-react";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { OpenBankingGuide } from "@/components/OpenBankingGuide";

type Auth = {
  authenticated: boolean;
  name?: string;
  userKey?: string;
  googleEnabled?: boolean;
  kakaoEnabled?: boolean;
};

type Transaction = {
  id: string;
  merchant: string;
  category: string;
  amount: number;
  type: "EXPENSE" | "INCOME";
  transactedAt: string;
  source: string;
};

type TransactionPage = {
  items: Transaction[];
  page: number;
  size: number;
  total: number;
  hasNext: boolean;
};

type Overview = {
  income: number;
  expense: number;
  remaining: number;
  categories: Record<string, number>;
  transactionCount: number;
  monthly: { month: string; income: number; expense: number }[];
};

const emptyOverview: Overview = {
  income: 0,
  expense: 0,
  remaining: 0,
  categories: {},
  transactionCount: 0,
  monthly: [],
};

type Budget = {
  month: string;
  amount: number;
};

type BankAccount = {
  id: string;
  institutionName: string;
  maskedNumber: string;
  balance: number;
  availableBalance: number;
  productName?: string;
  lastSyncedAt?: string;
};

type Banking = {
  enabled: boolean;
  connected: boolean;
  fullSyncConfigured: boolean;
  testMode: boolean;
  environment: "TESTBED" | "PRODUCTION";
  readiness: {
    code: string;
    message: string;
    realAccountData: boolean;
  };
  accounts: BankAccount[];
};

type SyncIssue = {
  accountId?: string;
  accountName: string;
  stage: "ACCOUNT_DIRECTORY" | "BALANCE" | "TRANSACTIONS";
  code: string;
  message: string;
};

const categoryColors = ["#ef7b45", "#e9b949", "#2f8f68", "#5c7caa", "#a78b7a"];
const money = (value: number) => `${value.toLocaleString("ko-KR")}원`;
const signOutUrl = "/api/auth/logout";
const writeHeaders = { "X-Saldo-Request": "web" };

async function responseMessage(response: Response, fallback: string) {
  try {
    const body = await response.json();
    return body.detail || body.message || fallback;
  } catch {
    return fallback;
  }
}

export default function HomePage() {
  const [auth, setAuth] = useState<Auth | null>(null);
  const [overview, setOverview] = useState<Overview>(emptyOverview);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [hasMoreTransactions, setHasMoreTransactions] = useState(false);
  const [transactionPage, setTransactionPage] = useState(0);
  const [loadingMoreTransactions, setLoadingMoreTransactions] = useState(false);
  const [budget, setBudget] = useState<Budget>({ month: new Date().toISOString().slice(0, 7), amount: 0 });
  const [banking, setBanking] = useState<Banking>({
    enabled: false,
    connected: false,
    fullSyncConfigured: false,
    testMode: true,
    environment: "TESTBED",
    readiness: {
      code: "CONFIGURATION_REQUIRED",
      message: "오픈뱅킹 설정을 확인하고 있습니다.",
      realAccountData: false,
    },
    accounts: [],
  });
  const [modal, setModal] = useState<"bank" | "add" | "budget" | null>(null);
  const [filter, setFilter] = useState<"all" | "EXPENSE" | "INCOME">("all");
  const [search, setSearch] = useState("");
  const [mobileNav, setMobileNav] = useState(false);
  const [toast, setToast] = useState("");
  const [saving, setSaving] = useState(false);
  const [syncIssues, setSyncIssues] = useState<SyncIssue[]>([]);
  const [bankingError, setBankingError] = useState("");

  const loadPrivateData = useCallback(async () => {
    const [overviewResult, transactionResult, budgetResult, bankingResult] = await Promise.allSettled([
      fetch("/api/overview", { credentials: "include", cache: "no-store" }),
      fetch("/api/transactions/page?page=0&size=50", { credentials: "include", cache: "no-store" }),
      fetch("/api/budget", { credentials: "include", cache: "no-store" }),
      fetch("/api/openbanking/accounts", { credentials: "include", cache: "no-store" }),
    ]);

    const coreResults = [overviewResult, transactionResult, budgetResult];
    if (coreResults.some((result) => result.status === "rejected" || !result.value.ok)) {
      throw new Error("가계부 데이터를 불러오지 못했습니다. 잠시 후 새로고침해 주세요.");
    }
    if (overviewResult.status === "fulfilled") setOverview(await overviewResult.value.json());
    if (transactionResult.status === "fulfilled") {
      const transactionPageResult: TransactionPage = await transactionResult.value.json();
      setTransactions(transactionPageResult.items);
      setTransactionPage(transactionPageResult.page);
      setHasMoreTransactions(transactionPageResult.hasNext);
    }
    if (budgetResult.status === "fulfilled") setBudget(await budgetResult.value.json());

    if (bankingResult.status === "fulfilled" && bankingResult.value.ok) {
      setBanking(await bankingResult.value.json());
      setBankingError("");
    } else {
      setBankingError("계좌 서비스에 일시적으로 연결하지 못했습니다. 로그인과 가계부 데이터는 정상적으로 사용할 수 있어요.");
    }
  }, []);

  async function loadMoreTransactions() {
    if (loadingMoreTransactions || !hasMoreTransactions) return;
    setLoadingMoreTransactions(true);
    try {
      const response = await fetch(`/api/transactions/page?page=${transactionPage + 1}&size=50`, {
        credentials: "include",
        cache: "no-store",
      });
      if (!response.ok) throw new Error("거래 내역을 더 불러오지 못했습니다.");
      const nextPage: TransactionPage = await response.json();
      setTransactions((current) => [...current, ...nextPage.items]);
      setTransactionPage(nextPage.page);
      setHasMoreTransactions(nextPage.hasNext);
    } catch (error) {
      flash(error instanceof Error ? error.message : "거래 내역을 더 불러오지 못했습니다.");
    } finally {
      setLoadingMoreTransactions(false);
    }
  }

  useEffect(() => {
    let active = true;
    async function initialize() {
      try {
        const response = await fetch("/api/auth/me", {
          credentials: "include",
          cache: "no-store",
        });
        const me: Auth = await response.json();
        if (!active) return;
        setAuth(me);
        const loginResult = new URLSearchParams(window.location.search).get("login");
        if (loginResult) {
          window.history.replaceState({}, "", window.location.pathname);
          setToast("로그인이 취소되었습니다.");
          window.setTimeout(() => setToast(""), 3000);
        }
        if (me.authenticated) {
          try {
            await loadPrivateData();
          } catch (error) {
            setToast(error instanceof Error ? error.message : "개인 데이터를 불러오지 못했습니다.");
          }
          const result = new URLSearchParams(window.location.search).get("openbanking");
          if (result) {
            window.history.replaceState({}, "", window.location.pathname);
            setToast(result === "connected"
              ? "계좌 연결이 완료되었습니다."
              : result === "error"
                ? "금융결제원에서 계좌 연결을 완료하지 못했습니다. 계좌 관리에서 다시 시도해 주세요."
                : "계좌 연결이 취소되었습니다.");
            window.setTimeout(() => setToast(""), 3000);
          }
        }
      } catch {
        if (active) setAuth({ authenticated: false });
      }
    }
    initialize();
    return () => {
      active = false;
    };
  }, [loadPrivateData]);

  const visibleTransactions = useMemo(
    () => transactions.filter((transaction) => {
      const matchesType = filter === "all" || transaction.type === filter;
      const keyword = search.trim().toLocaleLowerCase("ko-KR");
      const matchesSearch = !keyword || `${transaction.merchant} ${transaction.category}`.toLocaleLowerCase("ko-KR").includes(keyword);
      return matchesType && matchesSearch;
    }),
    [filter, search, transactions],
  );

  const categoryEntries = useMemo(
    () => Object.entries(overview.categories).sort((a, b) => b[1] - a[1]),
    [overview.categories],
  );

  const budgetPercent = budget.amount > 0
    ? Math.min(100, Math.round((overview.expense / budget.amount) * 100))
    : 0;

  const trendMaximum = Math.max(
    1,
    ...overview.monthly.flatMap((point) => [point.income, point.expense]),
  );

  function flash(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(""), 3000);
  }

  async function addTransaction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      const response = await fetch("/api/transactions", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...writeHeaders },
        body: JSON.stringify({
          merchant: String(data.get("merchant")),
          category: String(data.get("category")),
          amount: Number(data.get("amount")),
          type: String(data.get("type")),
          transactedAt: new Date().toISOString(),
        }),
      });
      if (!response.ok) throw new Error("저장하지 못했습니다.");
      await loadPrivateData();
      setModal(null);
      form.reset();
      flash("내 가계부에 안전하게 저장했어요.");
    } catch (error) {
      flash(error instanceof Error ? error.message : "저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function connectBank() {
    try {
      const response = await fetch("/api/openbanking/connect", {
        method: "POST",
        credentials: "include",
        headers: writeHeaders,
      });
      if (response.ok) {
        const result: { authorizeUrl: string } = await response.json();
        window.location.href = result.authorizeUrl;
        return;
      }
      if (response.status === 409) {
        flash("오픈뱅킹 운영기관 키 등록 후 계좌 연결이 활성화됩니다.");
      } else {
        flash("계좌 연결 요청을 시작하지 못했습니다.");
      }
    } catch {
      flash("계좌 연결 요청을 시작하지 못했습니다.");
    }
    setModal(null);
  }

  async function syncBank() {
    setSaving(true);
    try {
      const response = await fetch("/api/openbanking/sync", {
        method: "POST",
        credentials: "include",
        headers: writeHeaders,
      });
      if (!response.ok) {
        const fallback = response.status === 409
          ? "금융결제원 이용기관코드를 추가하면 잔액과 거래내역까지 동기화할 수 있습니다."
          : "계좌 데이터를 동기화하지 못했습니다.";
        throw new Error(await responseMessage(response, fallback));
      }
      const result: {
        syncedAccountCount: number;
        importedTransactionCount: number;
        fullSync: boolean;
        issues: SyncIssue[];
      } = await response.json();
      setSyncIssues(result.issues);
      await loadPrivateData();
      if (result.issues.length > 0) {
        const first = result.issues[0];
        flash(`${first.accountName}: ${first.message} (${first.code})`);
      } else {
        flash(result.fullSync
          ? `${result.syncedAccountCount}개 계좌를 새로고침하고 새 거래 ${result.importedTransactionCount}건을 반영했습니다.`
          : "연결 계좌 목록을 새로고침했습니다.");
      }
    } catch (error) {
      flash(error instanceof Error ? error.message : "계좌 데이터를 동기화하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function disconnectAccount(account: BankAccount) {
    if (!window.confirm(`${account.institutionName} ${account.maskedNumber} 계좌를 살도에서 제거할까요?\n기존에 가져온 거래내역은 가계부 기록으로 남습니다.`)) return;
    setSaving(true);
    try {
      const response = await fetch(`/api/openbanking/accounts/${account.id}`, {
        method: "DELETE",
        credentials: "include",
        headers: writeHeaders,
      });
      if (!response.ok) throw new Error(await responseMessage(response, "계좌 연결을 해제하지 못했습니다."));
      setSyncIssues((current) => current.filter((issue) => issue.accountId !== account.id));
      await loadPrivateData();
      flash("선택한 계좌를 살도에서 제거했습니다.");
    } catch (error) {
      flash(error instanceof Error ? error.message : "계좌 연결을 해제하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function disconnectAllAccounts() {
    if (!window.confirm("살도에 연결된 모든 계좌와 오픈뱅킹 연결 정보를 제거할까요?\n기존에 가져온 거래내역은 가계부 기록으로 남습니다.")) return;
    setSaving(true);
    try {
      const response = await fetch("/api/openbanking/connection", {
        method: "DELETE",
        credentials: "include",
        headers: writeHeaders,
      });
      if (!response.ok) throw new Error(await responseMessage(response, "전체 연결을 해제하지 못했습니다."));
      setSyncIssues([]);
      await loadPrivateData();
      setModal(null);
      flash("살도의 오픈뱅킹 연결 정보를 모두 제거했습니다.");
    } catch (error) {
      flash(error instanceof Error ? error.message : "전체 연결을 해제하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function deleteTransaction(transactionId: string) {
    if (!window.confirm("이 거래를 삭제할까요? 삭제 후에는 되돌릴 수 없습니다.")) return;
    const response = await fetch(`/api/transactions/${transactionId}`, {
      method: "DELETE",
      credentials: "include",
      headers: writeHeaders,
    });
    if (!response.ok) {
      flash("거래를 삭제하지 못했습니다.");
      return;
    }
    await loadPrivateData();
    flash("거래를 삭제했습니다.");
  }

  async function saveBudget(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    const form = event.currentTarget;
    const data = new FormData(form);
    try {
      const response = await fetch("/api/budget", {
        method: "PUT",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...writeHeaders },
        body: JSON.stringify({
          month: budget.month,
          amount: Number(data.get("amount")),
        }),
      });
      if (!response.ok) throw new Error("예산을 저장하지 못했습니다.");
      setBudget(await response.json());
      setModal(null);
      flash("이번 달 예산을 저장했어요.");
    } catch (error) {
      flash(error instanceof Error ? error.message : "예산을 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  function exportCsv() {
    const rows = [
      ["날짜", "구분", "내용", "카테고리", "금액", "입력 방식"],
      ...visibleTransactions.map((transaction) => [
        new Date(transaction.transactedAt).toLocaleDateString("ko-KR"),
        transaction.type === "INCOME" ? "수입" : "지출",
        transaction.merchant,
        transaction.category,
        String(transaction.amount),
        transaction.source === "MANUAL" ? "직접 입력" : "계좌 연동",
      ]),
    ];
    const csv = `\uFEFF${rows.map((row) => row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(",")).join("\n")}`;
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `살도-${budget.month}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  if (auth === null) {
    return (
      <main className="loading-screen">
        <span className="brand-mark"><Leaf size={20} /></span>
        <LoaderCircle className="spin" size={24} />
        <p>안전한 로그인 상태를 확인하고 있어요.</p>
      </main>
    );
  }

  if (!auth.authenticated) {
    return <LoginGate auth={auth} />;
  }

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileNav ? "open" : ""}`}>
        <button className="mobile-close" aria-label="메뉴 닫기" onClick={() => setMobileNav(false)}>
          <X size={20} />
        </button>
        <div className="brand">
          <span className="brand-mark"><Leaf size={18} /></span>
          <span>살도</span>
        </div>
        <nav aria-label="주요 메뉴">
          <a className="nav-item active" href="#dashboard"><LayoutGrid size={18} /> 한눈에 보기</a>
          <a className="nav-item" href="#transactions"><WalletCards size={18} /> 거래 내역</a>
          <a className="nav-item" href="#budget"><Target size={18} /> 이번 달</a>
          {banking.enabled && <a className="nav-item" href="#assets"><Landmark size={18} /> 연결 자산</a>}
        </nav>
        <div className="nav-spacer" />
        <div className="mini-card secure-card">
          <span className="mini-icon"><ShieldCheck size={16} /></span>
          <strong>내 데이터는 나만 볼 수 있어요</strong>
          <p>모든 거래는 로그인한 사용자 ID로 분리해 저장합니다.</p>
        </div>
        <nav className="nav-bottom">
          <button className="nav-item"><CircleHelp size={18} /> 도움말</button>
          <button className="nav-item"><Settings size={18} /> 설정</button>
        </nav>
        <a className="profile" href={signOutUrl}>
          <span className="avatar">회</span>
          <span><b>{auth.name ?? "회원"}님</b><small>개인 ID · {auth.userKey}</small></span>
          <LogOut size={15} />
        </a>
      </aside>

      <main className="main" id="dashboard">
        <header className="topbar">
          <button className="icon-button mobile-menu" aria-label="메뉴 열기" onClick={() => setMobileNav(true)}>
            <Menu size={20} />
          </button>
          <div className="welcome">
            <p>로그인된 개인 가계부</p>
            <h1>내 돈의 흐름을 한눈에 확인하세요.</h1>
          </div>
          <div className="top-actions">
            <button className="icon-button" aria-label="검색"><Search size={19} /></button>
            <button className="icon-button notification" aria-label="알림"><Bell size={19} /><i /></button>
            <button className="primary-button" onClick={() => setModal("add")}><Plus size={18} /> 내역 추가</button>
          </div>
        </header>

        <section className="privacy-status">
          <LockKeyhole size={15} />
          <span>현재 화면의 금액과 거래는 개인 ID <b>{auth.userKey}</b>에만 연결되어 있습니다.</span>
        </section>

        <section className="summary-grid" aria-label="개인 자산 요약">
          <article className="balance-card">
            <div className="card-head"><span>이번 달 남은 돈</span><span>실제 저장 데이터</span></div>
            <strong>{overview.remaining.toLocaleString("ko-KR")}<small>원</small></strong>
            <div className="balance-row">
              <span><i className="income-dot"><ArrowDownLeft size={13} /></i><em>수입</em><b>{money(overview.income)}</b></span>
              <span><i className="expense-dot"><ArrowUpRight size={13} /></i><em>지출</em><b>{money(overview.expense)}</b></span>
            </div>
          </article>

          {banking.enabled && <article className="account-card" id="assets">
            <div className="card-head">
              <span>연결된 자산</span>
              <button onClick={() => setModal("bank")} disabled={saving}>
                {banking.connected ? "계좌 관리" : "계좌 연결"} <ChevronRight size={14} />
              </button>
            </div>
            <strong>{banking.accounts.reduce((sum, account) => sum + account.balance, 0).toLocaleString("ko-KR")}<small>원</small></strong>
            {bankingError && <div className="account-service-error">{bankingError}</div>}
            {banking.accounts.length === 0 ? (
              <div className="account-empty">
                <span><Landmark size={19} /></span>
                <div><b>{banking.connected ? "등록된 계좌가 없습니다." : "연결된 계좌가 없습니다."}</b><small>금융결제원 화면에서 본인 계좌를 선택해 연결할 수 있어요.</small></div>
              </div>
            ) : (
              <div className="connected-accounts">
                {banking.accounts.map((account) => (
                  <div className="connected-account" key={account.id}>
                    <span><b>{account.institutionName}</b><small>{account.productName || account.maskedNumber}</small></span>
                    <strong>{money(account.balance)}</strong>
                  </div>
                ))}
              </div>
            )}
          </article>}

          <article className="budget-card" id="budget">
            <div className="card-head"><span>이번 달 예산</span><button onClick={() => setModal("budget")}>설정 <ChevronRight size={14} /></button></div>
            <div className="budget-progress">
              <div style={{ background: `conic-gradient(var(--green) ${budgetPercent}%, #e9eee8 0)` }}><span>{budgetPercent}%</span></div>
            </div>
            <div className="budget-copy">
              <strong>{budget.amount > 0 ? `${money(Math.max(0, budget.amount - overview.expense))} 남음` : "예산을 설정해 보세요"}</strong>
              <p>{budget.amount > 0 ? `${money(budget.amount)} 중 ${money(overview.expense)} 사용` : "월 지출 목표를 정하고 초과 여부를 확인할 수 있어요."}</p>
            </div>
          </article>
        </section>

        <section className="content-grid">
          <article className="panel trend-panel">
            <div className="panel-head">
              <div><span className="eyebrow">6개월 추이</span><h2>월별 수입과 지출</h2></div>
              <span className="data-badge"><TrendingUp size={12} /> 자동 분석</span>
            </div>
            <div className="trend-chart">
              {overview.monthly.map((point) => (
                <div className="trend-column" key={point.month}>
                  <div className="trend-bars">
                    <i className="trend-income" style={{ height: `${Math.max(3, (point.income / trendMaximum) * 100)}%` }} title={`수입 ${money(point.income)}`} />
                    <i className="trend-expense" style={{ height: `${Math.max(3, (point.expense / trendMaximum) * 100)}%` }} title={`지출 ${money(point.expense)}`} />
                  </div>
                  <span>{Number(point.month.slice(5))}월</span>
                </div>
              ))}
            </div>
            <div className="trend-legend"><span><i className="trend-income" />수입</span><span><i className="trend-expense" />지출</span></div>
          </article>

          <article className="panel spending-panel">
            <div className="panel-head">
              <div><span className="eyebrow">지출 분석</span><h2>이번 달 지출 카테고리</h2></div>
              <span className="data-badge">개인 데이터</span>
            </div>
            {categoryEntries.length === 0 ? (
              <EmptyState title="아직 분석할 지출이 없어요." description="첫 지출을 추가하면 카테고리 분석이 시작됩니다." onClick={() => setModal("add")} />
            ) : (
              <div className="category-bars">
                {categoryEntries.map(([category, amount], index) => {
                  const percentage = overview.expense ? Math.round((amount / overview.expense) * 100) : 0;
                  return (
                    <div className="category-bar" key={category}>
                      <div><span><i style={{ background: categoryColors[index % categoryColors.length] }} />{category}</span><b>{money(amount)} · {percentage}%</b></div>
                      <span><i style={{ width: `${percentage}%`, background: categoryColors[index % categoryColors.length] }} /></span>
                    </div>
                  );
                })}
              </div>
            )}
          </article>

          <article className="panel transaction-panel" id="transactions">
            <div className="panel-head">
              <div><span className="eyebrow">거래 내역</span><h2>내가 기록한 돈의 흐름</h2></div>
              <div className="panel-actions">
                <button className="text-button" onClick={exportCsv}><Download size={14} /> CSV</button>
                <button className="text-button" onClick={() => setModal("add")}><Plus size={14} /> 추가</button>
              </div>
            </div>
            <label className="transaction-search"><Search size={15} /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="내용이나 카테고리 검색" /></label>
            <div className="filter-tabs" role="tablist">
              {(["all", "EXPENSE", "INCOME"] as const).map((item) => (
                <button key={item} className={filter === item ? "active" : ""} onClick={() => setFilter(item)}>
                  {item === "all" ? "전체" : item === "EXPENSE" ? "지출" : "수입"}
                </button>
              ))}
            </div>
            {visibleTransactions.length === 0 ? (
              <EmptyState title="저장된 거래가 없습니다." description="가상 내역 없이 깨끗한 상태로 시작합니다." onClick={() => setModal("add")} />
            ) : (
              <div className="transaction-list">
                {visibleTransactions.map((transaction) => (
                  <div className="transaction" key={transaction.id}>
                    <span className={`tx-icon ${transaction.type === "INCOME" ? "income" : ""}`}>
                      {transaction.type === "INCOME" ? <ArrowDownLeft size={17} /> : <CreditCard size={17} />}
                    </span>
                    <span className="tx-name"><b>{transaction.merchant}</b><small>{transaction.category} · {transaction.source === "MANUAL" ? "직접 입력" : "계좌 연동"}</small></span>
                    <span className="tx-date">{new Intl.DateTimeFormat("ko-KR", { month: "short", day: "numeric" }).format(new Date(transaction.transactedAt))}</span>
                    <strong className={transaction.type === "INCOME" ? "income" : ""}>{transaction.type === "EXPENSE" ? "−" : "+"}{money(transaction.amount)}</strong>
                    <button className="delete-transaction" aria-label={`${transaction.merchant} 삭제`} onClick={() => deleteTransaction(transaction.id)}><Trash2 size={14} /></button>
                  </div>
                ))}
              </div>
            )}
            {hasMoreTransactions && (
              <button className="load-more-button" onClick={loadMoreTransactions} disabled={loadingMoreTransactions}>
                {loadingMoreTransactions ? "불러오는 중…" : "더 많은 거래 불러오기"}
              </button>
            )}
          </article>
        </section>
        <p className="disclaimer">가상 금융 데이터는 표시하지 않습니다. 모든 금액과 거래는 로그인한 계정에 저장한 기록을 기준으로 계산합니다.</p>
      </main>

      {modal && (
        <div className="modal-backdrop" role="presentation" onMouseDown={() => setModal(null)}>
          <section className="modal" role="dialog" aria-modal="true" onMouseDown={(event) => event.stopPropagation()}>
            <button className="modal-close" onClick={() => setModal(null)} aria-label="닫기"><X size={20} /></button>
            {modal === "bank" ? (
              <>
                <span className="modal-symbol"><Landmark size={22} /></span>
                <h2>{banking.connected ? "연결 계좌 관리" : "내 계좌 연결하기"}</h2>
                {banking.testMode && (
                  <div className="test-mode-notice">
                    <b>금융결제원 테스트 모드</b>
                    <span>현재는 실제 은행 잔액이 아니라 포털의 ‘테스트 정보 관리’에 등록한 응답 데이터가 조회됩니다.</span>
                  </div>
                )}
                {!banking.testMode && (
                  <div className="production-mode-notice">
                    <b>운영 API 설정</b>
                    <span>{banking.readiness.message}</span>
                  </div>
                )}
                <p>{banking.connected
                  ? "계좌를 더 추가하거나 연결된 계좌의 잔액과 거래내역을 새로고침할 수 있어요."
                  : "금융결제원 인증 화면에서 본인이 직접 동의해야 연결됩니다."}</p>
                {banking.connected && banking.accounts.length > 0 && (
                  <div className="bank-account-manager">
                    {banking.accounts.map((account) => (
                      <div className="bank-account-row" key={account.id}>
                        <span>
                          <b>{account.institutionName}</b>
                          <small>{account.productName || "연결 계좌"} · {account.maskedNumber}</small>
                          <small>{account.lastSyncedAt
                            ? `마지막 동기화 ${new Date(account.lastSyncedAt).toLocaleString("ko-KR")}`
                            : "아직 잔액을 동기화하지 못했습니다."}</small>
                        </span>
                        <span>
                          <strong>{money(account.balance)}</strong>
                          <button className="account-remove" onClick={() => disconnectAccount(account)} disabled={saving} aria-label={`${account.institutionName} 연결 해제`}>
                            <Trash2 size={14} /> 제거
                          </button>
                        </span>
                      </div>
                    ))}
                  </div>
                )}
                {syncIssues.length > 0 && (
                  <div className="sync-issues" role="status">
                    <b>동기화 확인이 필요해요</b>
                    {syncIssues.map((issue, index) => (
                      <p key={`${issue.accountId ?? "directory"}-${issue.stage}-${index}`}>
                        {issue.accountName} · {issue.stage === "BALANCE" ? "잔액" : issue.stage === "TRANSACTIONS" ? "거래내역" : "계좌목록"}:
                        {" "}{issue.message} <small>({issue.code})</small>
                        {issue.code === "A0308" && (
                          <> <a href="https://openapi.kftc.or.kr/" target="_blank" rel="noreferrer">금융결제원 포털 열기</a></>
                        )}
                      </p>
                    ))}
                  </div>
                )}
                <div className="bank-manager-actions">
                  {banking.connected && (
                    <button className="secondary-button" onClick={syncBank} disabled={saving}>
                      {saving ? "처리 중…" : banking.testMode ? "테스트 계좌 새로고침" : "전체 계좌 새로고침"}
                    </button>
                  )}
                  <button className="submit-button" onClick={connectBank} disabled={saving}>
                    {banking.connected ? "다른 계좌 추가 연결" : banking.testMode ? "테스트베드로 연결" : "오픈뱅킹으로 연결"}
                  </button>
                </div>
                <div className="security-note">살도는 계좌 비밀번호를 받거나 저장하지 않습니다. 금융결제원이 발급한 접근 토큰은 서버에서 암호화해 저장합니다.</div>
                {banking.connected && (
                  <button className="disconnect-all" onClick={disconnectAllAccounts} disabled={saving}>
                    오픈뱅킹 전체 연결 해제
                  </button>
                )}
                <small className="disconnect-note">여기서 제거하면 살도 서버의 연결 정보가 삭제됩니다. 금융결제원 자체 동의 철회는 금융결제원 또는 금융기관의 계좌정보통합관리 서비스에서도 할 수 있습니다.</small>
                {!banking.connected && <OpenBankingGuide />}
              </>
            ) : modal === "budget" ? (
              <form onSubmit={saveBudget}>
                <span className="modal-symbol"><PiggyBank size={22} /></span>
                <h2>이번 달 예산 설정</h2>
                <p>설정한 예산은 현재 로그인한 계정에만 저장됩니다.</p>
                <label>월<input value={budget.month} disabled /></label>
                <label>예산 금액<input name="amount" type="number" min="0" max="999999999999" defaultValue={budget.amount || ""} placeholder="예: 2000000" required /></label>
                <button className="submit-button" type="submit" disabled={saving}>{saving ? "저장 중…" : "예산 저장"}</button>
              </form>
            ) : (
              <AddTransactionForm onSubmit={addTransaction} saving={saving} />
            )}
          </section>
        </div>
      )}
      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}

function LoginGate({ auth }: { auth: Auth }) {
  return (
    <main className="login-page">
      <section className="login-copy">
        <div className="brand login-brand"><span className="brand-mark"><Leaf size={18} /></span><span>살도</span></div>
        <span className="login-kicker"><ShieldCheck size={14} /> 사용자별 완전 분리 저장</span>
        <h1>가상 데이터가 아닌,<br />나만의 가계부를 시작하세요.</h1>
        <p>로그인한 사용자에게만 개인 가계부를 열어드려요. 다른 사용자는 내 거래와 금액을 조회할 수 없습니다.</p>
        <div className="social-login-buttons">
          <a className={`real-login-button google-login ${auth.googleEnabled ? "" : "disabled"}`} href={auth.googleEnabled ? "/api/auth/google" : undefined} aria-disabled={!auth.googleEnabled}>
            <span className="social-logo google-logo">G</span> Google로 계속하기 <ChevronRight size={17} />
          </a>
          <a className={`real-login-button kakao-login ${auth.kakaoEnabled ? "" : "disabled"}`} href={auth.kakaoEnabled ? "/api/auth/kakao" : undefined} aria-disabled={!auth.kakaoEnabled}>
            <span className="social-logo kakao-logo">K</span> 카카오로 계속하기 <ChevronRight size={17} />
          </a>
        </div>
        <small>{auth.googleEnabled || auth.kakaoEnabled
          ? "공식 인증 화면을 사용하며 살도는 소셜 계정 비밀번호를 받거나 저장하지 않습니다."
          : "운영자가 Google 또는 카카오 OAuth 키를 등록하면 로그인이 활성화됩니다."}</small>
      </section>
      <section className="login-visual" aria-hidden="true">
        <div className="privacy-card">
          <span className="modal-symbol"><ShieldCheck size={22} /></span>
          <p>내 가계부</p>
          <strong>로그인 후 공개</strong>
          <div><span>수입</span><b>••••••원</b></div>
          <div><span>지출</span><b>••••••원</b></div>
          <footer><LockKeyhole size={13} /> 본인만 볼 수 있음</footer>
        </div>
      </section>
    </main>
  );
}

function EmptyState({ title, description, onClick }: { title: string; description: string; onClick: () => void }) {
  return (
    <div className="empty-state">
      <span><WalletCards size={20} /></span>
      <b>{title}</b>
      <p>{description}</p>
      <button onClick={onClick}><Plus size={14} /> 첫 내역 추가</button>
    </div>
  );
}

function AddTransactionForm({ onSubmit, saving }: { onSubmit: (event: FormEvent<HTMLFormElement>) => void; saving: boolean }) {
  return (
    <form onSubmit={onSubmit}>
      <span className="modal-symbol"><Plus size={22} /></span>
      <h2>내역 추가</h2>
      <p>입력한 내용은 현재 로그인한 개인 계정에만 저장됩니다.</p>
      <label>구분<select name="type"><option value="EXPENSE">지출</option><option value="INCOME">수입</option></select></label>
      <label>내용<input name="merchant" placeholder="예: 월급, 점심 식사" maxLength={120} required /></label>
      <label>금액<input name="amount" type="number" min="1" max="999999999999" placeholder="0" required /></label>
      <label>카테고리<select name="category"><option>식비</option><option>생활</option><option>교통</option><option>쇼핑</option><option>급여</option><option>기타</option></select></label>
      <button className="submit-button" type="submit" disabled={saving}>{saving ? "저장 중…" : "내 가계부에 저장"}</button>
    </form>
  );
}
