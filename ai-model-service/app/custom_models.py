import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, RegressorMixin
from sklearn.linear_model import LinearRegression, GammaRegressor
import statsmodels.api as sm
from xgboost import XGBRegressor

class StatsmodelsGLMPipeline(BaseEstimator, RegressorMixin):
    def __init__(self, preprocessor, family_name="NegativeBinomial", alpha=1.0, offset_col="exposure_time"):
        self.preprocessor = preprocessor
        self.family_name = family_name
        self.alpha = alpha
        self.offset_col = offset_col
        self.model_ = None
        self.results_ = None

    def fit(self, X, y):
        # Extract offset
        if self.offset_col and self.offset_col in X.columns:
            offset = np.log(X[self.offset_col].clip(lower=1e-6)).values
        else:
            offset = None

        # Preprocess features
        X_trans = self.preprocessor.fit_transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()

        # Add constant for intercept
        X_trans = sm.add_constant(X_trans, has_constant="add")

        # Select family
        if self.family_name == "NegativeBinomial":
            family = sm.families.NegativeBinomial(alpha=self.alpha)
        else:
            family = sm.families.Poisson()

        self.model_ = sm.GLM(y, X_trans, family=family, offset=offset)
        try:
            self.results_ = self.model_.fit()
        except ValueError:
            # Fallback to BFGS if IRLS fails due to numerical instability
            self.results_ = self.model_.fit(method="bfgs", maxiter=1000)
        return self

    def predict(self, X):
        if self.offset_col and self.offset_col in X.columns:
            offset = np.log(X[self.offset_col].clip(lower=1e-6)).values
        else:
            offset = None

        X_trans = self.preprocessor.transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        X_trans = sm.add_constant(X_trans, has_constant="add")
        return self.results_.predict(X_trans, offset=offset)

class XGBoostPoissonPipeline(BaseEstimator, RegressorMixin):
    def __init__(self, preprocessor, n_estimators=150, max_depth=3, learning_rate=0.05, subsample=0.8, colsample_bytree=0.8, random_state=42, offset_col="exposure_time"):
        self.preprocessor = preprocessor
        self.n_estimators = n_estimators
        self.max_depth = max_depth
        self.learning_rate = learning_rate
        self.subsample = subsample
        self.colsample_bytree = colsample_bytree
        self.random_state = random_state
        self.offset_col = offset_col
        self.model_ = None

    def fit(self, X, y):
        if self.offset_col and self.offset_col in X.columns:
            base_margin = np.log(X[self.offset_col].clip(lower=1e-6)).values
        else:
            base_margin = None

        X_trans = self.preprocessor.fit_transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()

        self.model_ = XGBRegressor(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            learning_rate=self.learning_rate,
            subsample=self.subsample,
            colsample_bytree=self.colsample_bytree,
            objective="count:poisson",
            random_state=self.random_state
        )
        self.model_.fit(X_trans, y, base_margin=base_margin)
        return self

    def predict(self, X):
        if self.offset_col and self.offset_col in X.columns:
            base_margin = np.log(X[self.offset_col].clip(lower=1e-6)).values
        else:
            base_margin = None

        X_trans = self.preprocessor.transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        return self.model_.predict(X_trans, base_margin=base_margin)


class GammaGLMPipeline(BaseEstimator, RegressorMixin):
    def __init__(self, preprocessor, alpha=0.01, max_iter=1000):
        self.preprocessor = preprocessor
        self.alpha = alpha
        self.max_iter = max_iter
        self.model_ = GammaRegressor(alpha=self.alpha, max_iter=self.max_iter)

    def fit(self, X, y):
        X_trans = self.preprocessor.fit_transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        self.model_.fit(X_trans, y)
        return self

    def predict(self, X):
        X_trans = self.preprocessor.transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        return self.model_.predict(X_trans)

class LognormalOLSPipeline(BaseEstimator, RegressorMixin):
    def __init__(self, preprocessor):
        self.preprocessor = preprocessor
        self.model_ = LinearRegression()
        self.smearing_factor_ = 1.0

    def fit(self, X, y):
        log_y = np.log(y.clip(lower=1e-6))
        X_trans = self.preprocessor.fit_transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        self.model_.fit(X_trans, log_y)

        log_preds = self.model_.predict(X_trans)
        residuals = log_y - log_preds
        self.smearing_factor_ = np.mean(np.exp(residuals))
        return self

    def predict(self, X):
        X_trans = self.preprocessor.transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        log_preds = self.model_.predict(X_trans)
        return np.exp(log_preds) * self.smearing_factor_

class XGBoostLognormalPipeline(BaseEstimator, RegressorMixin):
    def __init__(self, preprocessor, n_estimators=150, max_depth=3, learning_rate=0.05, subsample=0.8, colsample_bytree=0.8, random_state=42):
        self.preprocessor = preprocessor
        self.n_estimators = n_estimators
        self.max_depth = max_depth
        self.learning_rate = learning_rate
        self.subsample = subsample
        self.colsample_bytree = colsample_bytree
        self.random_state = random_state
        self.model_ = None
        self.smearing_factor_ = 1.0

    def fit(self, X, y):
        log_y = np.log(y.clip(lower=1e-6))
        X_trans = self.preprocessor.fit_transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        self.model_ = XGBRegressor(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            learning_rate=self.learning_rate,
            subsample=self.subsample,
            colsample_bytree=self.colsample_bytree,
            objective="reg:squarederror",
            random_state=self.random_state
        )
        self.model_.fit(X_trans, log_y)

        log_preds = self.model_.predict(X_trans)
        residuals = log_y - log_preds
        self.smearing_factor_ = np.mean(np.exp(residuals))
        return self

    def predict(self, X):
        X_trans = self.preprocessor.transform(X)
        if hasattr(X_trans, "toarray"):
            X_trans = X_trans.toarray()
        log_preds = self.model_.predict(X_trans)
        return np.exp(log_preds) * self.smearing_factor_

class FrequencySeverityModel(BaseEstimator, RegressorMixin):
    def __init__(self, frequency_pipeline, severity_pipeline):
        self.frequency_pipeline = frequency_pipeline
        self.severity_pipeline = severity_pipeline

    def fit(self, X, y):
        # We fit separately outside this wrapper to handle different subset targets
        return self

    def predict(self, X):
        # Total Expected Cost = Expected Frequency * Expected Severity
        freq_pred = self.frequency_pipeline.predict(X)
        sev_pred = self.severity_pipeline.predict(X)
        # Ensure predicted cost is non-negative
        return np.maximum(freq_pred * sev_pred, 0.0)
