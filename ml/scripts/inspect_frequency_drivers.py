import pandas as pd
import numpy as np

def main():
    df = pd.read_csv("data/generated/synthetic_insurance_claims.csv")
    df = df[df["exposure_time"] >= 0.1].copy()
    df["freq_rate"] = df["claim_count"] / df["exposure_time"]
    
    print("Average claim frequency rate by Gender:")
    print(df.groupby("gender")["freq_rate"].mean())
    
    print("\nAverage claim frequency rate by Distribution Channel:")
    print(df.groupby("distribution_channel")["freq_rate"].mean())
    
    print("\nAverage claim frequency rate by New Business:")
    print(df.groupby("new_business")["freq_rate"].mean())
    
    print("\nAverage claim frequency rate by Age Band:")
    print(df.groupby("age_band")["freq_rate"].mean())
    
    print("\nCorrelation of features with claim_count / frequency rate:")
    numeric_cols = ["age", "seniority_insured", "seniority_policy", "bmi", "blood_pressure", "prev_claim_count", "claim_free_years", "freq_rate"]
    print(df[numeric_cols].corr()["freq_rate"])

if __name__ == "__main__":
    main()
