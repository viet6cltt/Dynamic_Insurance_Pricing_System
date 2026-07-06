"""Pydantic schemas for the AI Model Service hybrid inference contract."""

from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field


class HealthRiskProfile(BaseModel):
    age: float = Field(..., ge=0, le=120)
    sex: Literal["male", "female"]
    bmi: float = Field(..., ge=10, le=80)
    children: int = Field(0, ge=0, le=20)
    smoker: Literal["yes", "no"]
    bloodPressure: float = Field(..., ge=70, le=220)
    exerciseFrequency: str
    preExistingCondition: bool
    occupationRisk: Literal["low", "moderate", "high"]


class HistoricalExperienceFeatures(BaseModel):
    pastClaimCount: int | None = Field(None, ge=0)
    totalPastClaimAmount: float | None = Field(None, ge=0)
    claimFreeYears: int | None = Field(None, ge=0, le=5)
    recencyWeightedClaimScore: float | None = Field(None, ge=0)


class PortfolioPricingProfile(BaseModel):
    gender: Literal["M", "F", "male", "female"] | None = None
    typeProduct: str | None = None
    typePolicy: str | None = None
    reimbursement: str | None = None
    exposureTime: float | None = Field(None, ge=0)
    seniorityInsured: float | None = Field(None, ge=0)
    newBusiness: str | None = None
    distributionChannel: str | None = None
    prevCostClaimsYear: float | None = Field(None, ge=0)
    prevNMedicalServices: float | None = Field(None, ge=0)
    prevHadClaimOrService: bool | None = None
    claimFreePreviousYear: bool | None = None


class HealthPricingPredictionRequest(BaseModel):
    productType: Literal["HEALTH"]
    riskProfile: HealthRiskProfile
    portfolioProfile: PortfolioPricingProfile | None = None
    historicalExperienceFeatures: HistoricalExperienceFeatures | None = None


class ExplanationItem(BaseModel):
    feature: str
    sourceFeature: str | None = None
    value: float | None = None
    contribution: float | None = None
    impact: Literal["increase", "decrease", "neutral"]
    readableReason: str | None = None
    reason: str | None = None
    approximate: bool = False


class PricingExplanationPayload(BaseModel):
    topRiskFactors: list[ExplanationItem]
    featureContributions: list[ExplanationItem] = []
    shapValues: list[ExplanationItem] = []
    method: str
    generatedAt: datetime


class PortfolioModelOutput(BaseModel):
    status: Literal["configured", "not_configured"]
    modelVersion: str | None = None
    modelSource: Literal["mlflow", "local"] | None = None
    registryModelName: str | None = None
    registryAlias: str | None = None
    registryVersion: str | None = None
    predictedAnnualClaimCost: float | None = None
    rawPortfolioRiskFactor: float | None = None
    portfolioRiskFactor: float | None = None
    portfolioModelExplanation: PricingExplanationPayload | None = None
    message: str | None = None


class HealthRiskModelOutput(BaseModel):
    status: Literal["configured", "not_configured"]
    modelVersion: str | None = None
    modelSource: Literal["mlflow", "local"] | None = None
    registryModelName: str | None = None
    registryAlias: str | None = None
    registryVersion: str | None = None
    predictedHealthCost: float | None = None
    baselineHealthCost: float | None = None
    rawHealthRiskFactor: float | None = None
    healthRiskFactor: float | None = None
    riskLevel: Literal["LOW", "MODERATE", "HIGH"] | None = None
    healthRiskExplanation: PricingExplanationPayload | None = None
    message: str | None = None


class HealthPricingPredictionResponse(BaseModel):
    modelVersion: str
    portfolioModel: PortfolioModelOutput
    healthRiskModel: HealthRiskModelOutput
    finalPremiumCalculatedBy: Literal["Pricing Service / Rating Engine"]
    finalPremiumFormula: str
    frequencyModelVersion: str | None = None
    severityModelVersion: str | None = None
    frequencyPrediction: float | None = None
    predictedFrequencyAnnual: float | None = None
    predictedSeverity: float | None = None
    predictedPurePremium: float | None = None
    baselinePurePremium: float | None = None
    rawRiskFactor: float | None = None
    appliedRiskFactor: float | None = None
    riskFactors: list[dict] = []
    explanationStatus: Literal["available", "partial", "unavailable"] | None = None


class ModelComponentMetadata(BaseModel):
    status: Literal["configured", "not_configured"]
    modelVersion: str | None = None
    selectedModel: str | None = None
    modelSource: Literal["mlflow", "local"] | None = None
    registryModelName: str | None = None
    registryAlias: str | None = None
    registryVersion: str | None = None
    trainingDate: str | None = None
    featureList: list[str]
    targetColumn: str | None = None
    purpose: str


class ModelMetadataResponse(BaseModel):
    serviceVersion: str
    portfolioModel: ModelComponentMetadata
    healthRiskModel: ModelComponentMetadata
    noFinalPremiumInModel: bool
    finalPremiumFormula: str


class PurePremiumPredictionRequest(BaseModel):
    age: float = Field(..., ge=0, le=120)
    seniority_insured: float | None = Field(None, ge=0)
    seniority_policy: float | None = Field(None, ge=0)
    bmi: float = Field(..., ge=10, le=80)
    blood_pressure: float = Field(..., ge=70, le=220)
    prev_claim_count: float | None = Field(None, ge=0)
    prev_claim_cost: float | None = Field(None, ge=0)
    claim_free_years: float | None = Field(None, ge=0)
    years_with_history: float | None = Field(None, ge=0)
    type_policy: str | None = None
    type_policy_dg: str | None = None
    type_product: str
    reimbursement: str | None = None
    new_business: str | None = None
    distribution_channel: str | None = None
    smoker: Literal["yes", "no"]
    pre_existing_condition: bool | str
    exercise_frequency: str
    occupation_risk: Literal["low", "moderate", "high"]
    prev_had_claim: bool | str | None = None
    claim_free_previous_year: bool | str | None = None
    exposure_time: float = Field(1.0, gt=0)
    prev_average_claim_severity: float | None = Field(None, ge=0)


class PurePremiumPredictionResponse(BaseModel):
    modelVersion: str
    frequencyModelVersion: str | None = None
    severityModelVersion: str | None = None
    frequencyPrediction: float
    predictedFrequencyAnnual: float
    predictedSeverity: float
    predictedPurePremium: float
    baselinePurePremium: float
    rawRiskFactor: float
    appliedRiskFactor: float
    riskFactors: list[dict]
    explanationStatus: Literal["available", "partial", "unavailable"]


class TrainingJobCreateRequest(BaseModel):
    modelType: Literal["FREQUENCY", "SEVERITY"]


class PromoteRejectRequest(BaseModel):
    candidateVersion: int
    reason: str | None = None
