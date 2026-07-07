# Dynamic Insurance Pricing System

## Frequency-Severity Health Insurance Pricing

The pricing flow uses a minimal Frequency-Severity strategy. AI Model Service
predicts annual claim frequency and average claim severity. Pricing Service
recalculates `purePremium`, copies `loadingRate` from the selected Coverage Plan,
and calculates the final quote premium.

```text
purePremium =
  predictedAnnualFrequency
  * predictedAverageSeverity

finalPremium =
  purePremium
  * (1 + loadingRate)
```

`loadingRate` is configured per Coverage Plan in Product Service and stored as a
decimal: `0.10` means 10%, `0.20` means 20%. Risk level is display/explanation
metadata only; it is not used in the final premium formula.

## Chỉ Mục Dữ Liệu Huấn Luyện

Nguồn dữ liệu: `data/health_insurance_portfolio.csv`, bản CSV chuyển đổi từ dữ
liệu danh mục hợp đồng bảo hiểm sức khỏe. Dataset này được dùng cho Model 1 -
`Portfolio Expected Cost Model`, nhằm học quan hệ giữa hồ sơ hợp đồng, lịch sử
sử dụng/bồi thường và chi phí claim kỳ hiện tại.

| Nhóm feature | Ví dụ thuộc tính | Vai trò |
| --- | --- | --- |
| Hồ sơ khách hàng | `age`, `gender` | Yếu tố nhân khẩu học ảnh hưởng đến xác suất phát sinh chi phí y tế và mức độ sử dụng bảo hiểm. |
| Thông tin sản phẩm/hợp đồng | `type_product`, `type_policy`, `reimbursement`, `new_business`, `distribution_channel` | Mô tả loại sản phẩm, cấu trúc hoàn trả, kênh phân phối và trạng thái hợp đồng mới/cũ; dùng để phản ánh khác biệt rủi ro theo thiết kế sản phẩm. |
| Thời gian tham gia | `exposure_time`, `seniority_insured` | Đo mức độ phơi nhiễm rủi ro trong kỳ và độ lâu năm của người được bảo hiểm. |
| Lịch sử claim/sử dụng dịch vụ | `prev_cost_claims_year`, `prev_n_medical_services`, `prev_had_claim_or_service`, `claim_free_previous_year` | Nhóm trọng tâm của Model 1, phản ánh kinh nghiệm bảo hiểm thực tế của khách hàng trong kỳ trước. |
| Biến mục tiêu (target) | `claim_count`, `annual_claim_cost`, `average_claim_severity` | Dùng để huấn luyện dự báo tần suất claim và severity trung bình. |

Nguồn dữ liệu: `data/health_insurance_cost_and_risk_dataset.csv`. Dataset này
được dùng cho Model 2 - `Health Risk Modifier Model`, nhằm học quan hệ giữa hồ
sơ sức khỏe hiện tại và chi phí y tế lịch sử.

| Nhóm feature | Ví dụ thuộc tính | Vai trò |
| --- | --- | --- |
| Hồ sơ khách hàng | `age`, `sex`, `children` | Yếu tố nhân khẩu học và quy mô phụ thuộc ảnh hưởng đến chi phí y tế dự kiến. |
| Chỉ số sức khỏe | `bmi`, `blood_pressure`, `pre_existing_condition` | Phản ánh tình trạng sức khỏe hiện tại, bệnh nền và các dấu hiệu có thể làm tăng rủi ro claim. |
| Hành vi/lối sống | `smoker`, `exercise_frequency` | Mô tả thói quen có ảnh hưởng trực tiếp đến rủi ro sức khỏe và chi phí điều trị. |
| Rủi ro nghề nghiệp | `occupation_risk` | Đại diện cho mức độ rủi ro trong môi trường làm việc, được dùng để điều chỉnh chi phí sức khỏe kỳ vọng. |
| Biến bị loại khỏi training | `region`, `annual_income`, các cột premium/final premium nếu xuất hiện | Không dùng làm feature trong Model 2 để tránh nhiễu hoặc rò rỉ thông tin giá sau định phí. |
| Biến mục tiêu (target) | `charges` hoặc severity tương đương | Dùng làm tín hiệu severity khi xây dựng mô hình chi phí trung bình. |

## Chiến Lược Định Giá

Hệ thống áp dụng chiến lược Frequency-Severity tối giản. Mục tiêu là tách phần
rủi ro kỹ thuật (`purePremium`) khỏi phần loading nghiệp vụ (`loadingRate`).

Luồng định giá chính:

1. Product Service quản lý sản phẩm và các gói quyền lợi. Mỗi coverage plan có
   `sumInsured` và `loadingRate`.
2. Customer nhập hồ sơ người được bảo hiểm và các thông tin rủi ro động theo
   schema sản phẩm, ví dụ BMI, hút thuốc, bệnh nền, nghề nghiệp hoặc tần suất
   vận động.
3. Pricing Service lấy dữ liệu người được bảo hiểm, gói bảo hiểm, lịch sử hợp
   đồng/bồi thường và gọi AI Model Service để nhận:
   `predictedAnnualFrequency`, `predictedAverageSeverity`, `purePremium`,
   `riskLevel`, model versions và explanation.
4. Pricing Service kiểm tra lại:

```text
purePremium =
  predictedAnnualFrequency
  * predictedAverageSeverity
```

5. Rating Engine tính phí cuối cùng:

Công thức đang được triển khai trong `RatingEngine`:

```text
finalPremium =
  purePremium
  * (1 + loadingRate)
```

Kết quả báo giá lưu snapshot `loadingRate`, `purePremium`, `finalPremium`,
phiên bản frequency/severity model và explanation để báo giá cũ không đổi khi
admin chỉnh Coverage Plan sau này.

## Recommended Local Flow

Install dependencies:

```bash
python3 -m pip install -r ai-model-service/requirements.txt
```

Start MLflow Model Registry locally:

```bash
cd infra/docker/mlflow
cp .env.example .env
docker compose up -d --build
cd ../../..
export MLFLOW_TRACKING_URI=http://127.0.0.1:15000
export MLFLOW_S3_ENDPOINT_URL=http://127.0.0.1:9002
export AWS_ACCESS_KEY_ID=minioadmin
export AWS_SECRET_ACCESS_KEY=minioadminpassword
```

Train Model 2:

```bash
python3 ml/scripts/train_health_risk_modifier_model.py --mlflow
```

With `--mlflow`, every trained candidate model is registered as a separate
version under the configured registry name. The candidate with the lowest
holdout RMSE receives the model-version tag `stage=Production` and the
registered-model alias `Production`.

Train Model 1:

```bash
python3 ml/scripts/convert_portfolio_excel_to_csv.py
python3 ml/scripts/train_portfolio_expected_cost_model.py --mlflow
```

Generate Model 2 explanation examples:

```bash
python3 ml/scripts/explain_health_risk_modifier_model.py
```

Open or execute the hybrid EDA notebook:

```bash
jupyter notebook ml/notebooks/health_pricing_eda.ipynb
jupyter nbconvert --execute ml/notebooks/health_pricing_eda.ipynb --to notebook --inplace
```

## Outputs

- Model 2 artifact: `ml/artifacts/health_risk_modifier_model.joblib`
- Model 1 artifact: `ml/artifacts/portfolio_expected_cost_model.joblib`
- Model 2 metadata: `ml/artifacts/health_risk_modifier_metadata.json`
- Model 1 metadata: `ml/artifacts/portfolio_expected_cost_metadata.json`
- Model 2 report: `ml/reports/health_risk_modifier_model_report.md`
- Model 1 report: `ml/reports/portfolio_expected_cost_model_report.md`
- Model comparison: `ml/reports/health_risk_modifier_comparison.csv`
- Explanation examples: `ml/reports/health_risk_modifier_shap_examples.json`

Legacy one-model scripts are kept for reference, but the hybrid flow above is
the preferred implementation.
