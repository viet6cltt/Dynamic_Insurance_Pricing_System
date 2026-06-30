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
