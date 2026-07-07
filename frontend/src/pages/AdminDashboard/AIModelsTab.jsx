import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  BarChart3,
  CheckCircle,
  GitCompareArrows,
  Loader2,
  Play,
  RefreshCcw,
  ThumbsDown,
  ThumbsUp,
} from "lucide-react";
import { adminService } from "../../services/adminService";

const MODEL_TYPES = [
  { id: "FREQUENCY", label: "Frequency Model", description: "Expected claim count per exposure period" },
  { id: "SEVERITY", label: "Severity Model", description: "Expected claim cost per claim" },
];

const METRIC_LABELS = {
  CV_PoissonDeviance: "CV Poisson Deviance",
  CV_NormalizedGini: "CV Normalized Gini",
  CV_Top_10_pct_Lift: "CV Top 10% Lift",
  PoissonDeviance: "Poisson Deviance",
  NormalizedGini: "Normalized Gini",
  Top_10_pct_Lift: "Top 10% Lift",
  CV_MAE: "CV MAE",
  CV_GammaDeviance: "CV Gamma Deviance",
  CV_PearsonCorrelation: "CV Pearson Correlation",
  MAE: "MAE",
  GammaDeviance: "Gamma Deviance",
  PearsonCorrelation: "Pearson Correlation",
};

function formatNumber(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return "-";
  const number = Number(value);
  if (Math.abs(number) >= 1000) return number.toLocaleString("vi-VN", { maximumFractionDigits: 2 });
  return number.toLocaleString("vi-VN", { maximumFractionDigits: 4 });
}

function formatDateTime(value) {
  if (!value) return "Chưa có";
  return new Date(value).toLocaleString("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
}

function StatusPill({ status }) {
  const normalized = (status || "UNKNOWN").toUpperCase();
  const style = {
    COMPLETED: "bg-emerald-50 text-emerald-700 border-emerald-100",
    RUNNING: "bg-blue-50 text-blue-700 border-blue-100",
    QUEUED: "bg-amber-50 text-amber-700 border-amber-100",
    FAILED: "bg-rose-50 text-rose-700 border-rose-100",
  }[normalized] || "bg-gray-50 text-gray-600 border-gray-200";
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-lg border text-xs font-bold ${style}`}>
      {normalized}
    </span>
  );
}

function ModelVersionPanel({ title, model, emptyText }) {
  return (
    <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm min-h-[178px]">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">{title}</p>
        {model?.version && (
          <span className="px-2.5 py-1 rounded-lg bg-gray-50 border border-gray-200 text-xs font-bold text-gray-700">
            v{model.version}
          </span>
        )}
      </div>
      {model ? (
        <div className="mt-4 space-y-3">
          <div>
            <p className="text-lg font-extrabold text-gray-950">{model.algorithm || "Unknown algorithm"}</p>
            <p className="text-xs text-gray-500 mt-1">{model.modelName}</p>
          </div>
          <div className="grid grid-cols-2 gap-3 text-xs">
            <div className="bg-gray-50 rounded-xl border border-gray-100 p-3">
              <p className="text-gray-400 font-semibold">Run ID</p>
              <p className="font-mono text-gray-700 truncate mt-1">{model.runId || "-"}</p>
            </div>
            <div className="bg-gray-50 rounded-xl border border-gray-100 p-3">
              <p className="text-gray-400 font-semibold">Status</p>
              <p className="font-bold text-gray-800 mt-1">{model.tags?.deployment_status || "ACTIVE"}</p>
            </div>
          </div>
        </div>
      ) : (
        <div className="h-full min-h-[110px] flex items-center text-sm text-gray-400">{emptyText}</div>
      )}
    </div>
  );
}

export default function AIModelsTab({ triggerToast }) {
  const [modelType, setModelType] = useState("FREQUENCY");
  const [modelStatus, setModelStatus] = useState(null);
  const [comparison, setComparison] = useState(null);
  const [activeJob, setActiveJob] = useState(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState("");
  const [error, setError] = useState("");
  const [reason, setReason] = useState("");

  const selectedModel = useMemo(
    () => MODEL_TYPES.find((item) => item.id === modelType) || MODEL_TYPES[0],
    [modelType]
  );

  const loadModelData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [status, compare] = await Promise.all([
        adminService.getAiModel(modelType),
        adminService.getAiModelComparison(modelType),
      ]);
      setModelStatus(status);
      setComparison(compare);
      if (status?.latestTrainingJob && ["QUEUED", "RUNNING"].includes(status.latestTrainingJob.status)) {
        setActiveJob(status.latestTrainingJob);
      }
    } catch (err) {
      console.error(err);
      setError("Không thể tải thông tin AI model.");
    } finally {
      setLoading(false);
    }
  }, [modelType]);

  useEffect(() => {
    loadModelData();
  }, [loadModelData]);

  useEffect(() => {
    if (!activeJob?.id || !["QUEUED", "RUNNING"].includes(activeJob.status)) return undefined;
    const timer = setInterval(async () => {
      try {
        const job = await adminService.getTrainingJob(activeJob.id);
        setActiveJob(job);
        if (!["QUEUED", "RUNNING"].includes(job.status)) {
          await loadModelData();
          triggerToast?.(job.status === "COMPLETED" ? "Training job đã hoàn tất." : "Training job thất bại.", job.status !== "COMPLETED");
        }
      } catch (err) {
        console.error(err);
      }
    }, 4000);
    return () => clearInterval(timer);
  }, [activeJob, loadModelData, triggerToast]);

  const handleTrainAgain = async () => {
    setActionLoading("train");
    setError("");
    try {
      const job = await adminService.createTrainingJob(modelType);
      setActiveJob(job);
      triggerToast?.(`Đã tạo training job #${job.id} cho ${selectedModel.label}.`);
    } catch (err) {
      console.error(err);
      triggerToast?.(err.response?.data?.detail || "Không thể tạo training job.", true);
    } finally {
      setActionLoading("");
    }
  };

  const handlePromote = async () => {
    const version = comparison?.candidate?.version;
    if (!version) return;
    setActionLoading("promote");
    try {
      await adminService.promoteAiModel(modelType, version, reason || "Promoted from Admin Dashboard");
      setReason("");
      triggerToast?.(`Đã promote ${selectedModel.label} version ${version}.`);
      await loadModelData();
    } catch (err) {
      console.error(err);
      triggerToast?.(err.response?.data?.detail || "Promote thất bại.", true);
    } finally {
      setActionLoading("");
    }
  };

  const handleReject = async () => {
    const version = comparison?.candidate?.version;
    if (!version) return;
    setActionLoading("reject");
    try {
      await adminService.rejectAiModel(modelType, version, reason || "Rejected from Admin Dashboard");
      setReason("");
      triggerToast?.(`Đã reject ${selectedModel.label} version ${version}.`);
      await loadModelData();
    } catch (err) {
      console.error(err);
      triggerToast?.(err.response?.data?.detail || "Reject thất bại.", true);
    } finally {
      setActionLoading("");
    }
  };

  const metricRows = Object.entries(comparison?.differences || {});
  const hasActiveJob = activeJob && ["QUEUED", "RUNNING"].includes(activeJob.status);

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-start flex-wrap gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">AI Model Lifecycle</h2>
          <p className="text-gray-500 text-sm mt-0.5">Train, review, promote hoặc reject Frequency/Severity candidates</p>
        </div>
        <button
          onClick={loadModelData}
          disabled={loading}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-white border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold text-gray-700 transition-all"
        >
          <RefreshCcw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} />
          Làm mới
        </button>
      </div>

      <div className="bg-white border border-gray-100 rounded-2xl p-2 shadow-sm inline-flex gap-2">
        {MODEL_TYPES.map((item) => (
          <button
            key={item.id}
            onClick={() => {
              setModelType(item.id);
              setActiveJob(null);
              setReason("");
            }}
            className={`px-4 py-2.5 rounded-xl text-sm font-bold transition-all ${
              modelType === item.id ? "bg-slate-900 text-white" : "text-gray-600 hover:bg-gray-50"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="bg-red-50 border border-red-100 rounded-xl p-4 flex items-center space-x-3 text-red-800 text-sm shadow-sm">
          <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
          <span className="font-medium">{error}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_320px] gap-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <ModelVersionPanel
            title="Production"
            model={comparison?.production || modelStatus?.production}
            emptyText="Chưa có champion/Production model trong MLflow."
          />
          <ModelVersionPanel
            title="Candidate"
            model={comparison?.candidate || modelStatus?.candidate}
            emptyText="Chưa có candidate mới. Hãy bấm Train Again để tạo candidate."
          />
        </div>

        <div className="bg-white border border-gray-100 rounded-2xl p-5 shadow-sm space-y-4">
          <div>
            <p className="text-xs font-bold text-gray-400 uppercase tracking-wider">Selected Model</p>
            <h3 className="text-lg font-extrabold text-gray-950 mt-1">{selectedModel.label}</h3>
            <p className="text-xs text-gray-500 mt-1">{selectedModel.description}</p>
          </div>

          {activeJob && (
            <div className="bg-gray-50 rounded-xl border border-gray-100 p-3 text-sm space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-bold text-gray-800">Job #{activeJob.id}</span>
                <StatusPill status={activeJob.status} />
              </div>
              <p className="text-xs text-gray-500">Stage: {activeJob.current_stage || "-"}</p>
              <p className="text-xs text-gray-500">Requested: {formatDateTime(activeJob.requested_at)}</p>
              {activeJob.failure_reason && <p className="text-xs text-rose-600">{activeJob.failure_reason}</p>}
            </div>
          )}

          <button
            onClick={handleTrainAgain}
            disabled={Boolean(actionLoading) || hasActiveJob}
            className="w-full inline-flex items-center justify-center gap-2 px-4 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white rounded-xl text-sm font-bold transition-all"
          >
            {actionLoading === "train" ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
            Train Again
          </button>

          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Reason for promote/reject"
            className="w-full min-h-20 bg-gray-50 border border-gray-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />

          <div className="grid grid-cols-2 gap-3">
            <button
              onClick={handlePromote}
              disabled={!comparison?.candidate?.version || Boolean(actionLoading)}
              className="inline-flex items-center justify-center gap-2 px-3 py-2.5 bg-emerald-600 hover:bg-emerald-700 disabled:bg-gray-300 text-white rounded-xl text-sm font-bold transition-all"
            >
              {actionLoading === "promote" ? <Loader2 className="w-4 h-4 animate-spin" /> : <ThumbsUp className="w-4 h-4" />}
              Promote
            </button>
            <button
              onClick={handleReject}
              disabled={!comparison?.candidate?.version || Boolean(actionLoading)}
              className="inline-flex items-center justify-center gap-2 px-3 py-2.5 bg-rose-600 hover:bg-rose-700 disabled:bg-gray-300 text-white rounded-xl text-sm font-bold transition-all"
            >
              {actionLoading === "reject" ? <Loader2 className="w-4 h-4 animate-spin" /> : <ThumbsDown className="w-4 h-4" />}
              Reject
            </button>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
              <GitCompareArrows className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-gray-950">Offline Metrics Comparison</h3>
              <p className="text-xs text-gray-500">Difference is candidate minus production.</p>
            </div>
          </div>
          {comparison?.improved ? (
            <span className="inline-flex items-center gap-1.5 text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-100 px-2.5 py-1 rounded-lg">
              <CheckCircle className="w-3.5 h-3.5" />
              Candidate looks better
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 text-xs font-bold text-gray-600 bg-gray-50 border border-gray-100 px-2.5 py-1 rounded-lg">
              <BarChart3 className="w-3.5 h-3.5" />
              Manual review
            </span>
          )}
        </div>

        {loading ? (
          <div className="py-16 flex items-center justify-center text-gray-400">
            <Loader2 className="w-7 h-7 animate-spin" />
          </div>
        ) : metricRows.length === 0 ? (
          <div className="py-16 text-center text-sm text-gray-400">
            Chưa đủ production và candidate metrics để so sánh.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                  <th className="py-4 px-6">Metric</th>
                  <th className="py-4 px-6 text-right">Production</th>
                  <th className="py-4 px-6 text-right">Candidate</th>
                  <th className="py-4 px-6 text-right">Difference</th>
                  <th className="py-4 px-6">Direction</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {metricRows.map(([metric, item]) => (
                  <tr key={metric} className="hover:bg-gray-50/30">
                    <td className="py-4 px-6 font-bold text-gray-900">{METRIC_LABELS[metric] || metric}</td>
                    <td className="py-4 px-6 text-right font-mono text-gray-700">{formatNumber(item.production)}</td>
                    <td className="py-4 px-6 text-right font-mono text-gray-700">{formatNumber(item.candidate)}</td>
                    <td className={`py-4 px-6 text-right font-mono font-bold ${item.improved ? "text-emerald-700" : item.improved === false ? "text-rose-700" : "text-gray-700"}`}>
                      {formatNumber(item.difference)}
                    </td>
                    <td className="py-4 px-6">
                      {item.improved === true && <span className="text-xs font-bold text-emerald-700">Improved</span>}
                      {item.improved === false && <span className="text-xs font-bold text-rose-700">Worse</span>}
                      {item.improved === null && <span className="text-xs font-bold text-gray-500">Neutral</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
