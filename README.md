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

## Chạy Bằng Docker Compose

File compose đầy đủ nằm ở `docker-compose.full.yml`. File này dựng backend,
các database, Kafka, Redis, MinIO, MLflow, Mailpit, Prometheus và Grafana.
Frontend hiện chạy riêng bằng Vite.

Chuẩn bị biến môi trường ở root project:

```bash
cp docker-compose.full.env.example .env
```

Sửa `.env` trước khi chạy nếu dùng OAuth/SMTP thật. Các biến quan trọng:

| Biến | Khi nào cần | Ghi chú |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID` | Bắt buộc nếu bật đăng nhập Google thật | OAuth Client ID trong Google Cloud Console. |
| `GOOGLE_CLIENT_SECRET` | Bắt buộc nếu bật đăng nhập Google thật | Chỉ đặt ở backend/root `.env`, không đưa vào frontend. |
| `GOOGLE_REDIRECT_PATH` | Bắt buộc nếu khác mặc định | Mặc định `/login/oauth2/code/google`. |
| `CLIENT_APP_URI` | Khi frontend không chạy ở `http://localhost:3000` | Cũng dùng để build Google redirect URL. |
| `APP_CORS_ALLOWED_ORIGINS` | Khi frontend đổi host/port | Ví dụ `http://localhost:3000`. |
| `CLIENT_SECRET` | Khi dùng OAuth client nội bộ thật | Mặc định dev là `insu-secret-key`; đổi khi chạy môi trường chia sẻ. |
| `REDIS_PASSWORD` | Nên đổi ngoài local dev | Phải khớp Redis, API Gateway và Product Service. |
| `MAIL_*` | Chỉ cần khi gửi mail thật | Mặc định dùng Mailpit local, không cần username/password. |
| `MLFLOW_POSTGRES_PASSWORD` | Nên đổi ngoài local dev | Mật khẩu Postgres cho MLflow. |
| `MLFLOW_S3_SECRET_ACCESS_KEY` | Nên đổi ngoài local dev | Secret key MinIO/S3 cho MLflow artifacts. |
| `GRAFANA_ADMIN_PASSWORD` | Nên đổi ngoài local dev | Mặc định là `admin`. |

Với Google OAuth, trong Google Cloud Console cần cấu hình Authorized redirect
URI khớp với frontend:

```text
http://localhost:3000/login/oauth2/code/google
```

Frontend cũng cần public Google client id, nhưng không được chứa client secret:

```bash
cp frontend/.env.example frontend/.env
```

Sửa `frontend/.env`:

```text
VITE_API_URL=http://localhost:8080
VITE_AUTH_URL=http://localhost:9000
VITE_GG_CLIENT_ID=<GOOGLE_CLIENT_ID>
VITE_GOOGLE_REDIRECT_URI=http://localhost:3000/login/oauth2/code/google
```

Chạy toàn bộ backend stack:

```bash
docker compose --env-file .env -f docker-compose.full.yml up -d --build
```

Theo dõi log khi cần:

```bash
docker compose --env-file .env -f docker-compose.full.yml logs -f apigateway authorization-server ai-model-service
```

Chạy frontend:

```bash
cd frontend
npm install
npm run dev -- --host 0.0.0.0 --port 3000
```

Các URL thường dùng:

| Thành phần | URL |
| --- | --- |
| Frontend | `http://localhost:3000` |
| API Gateway | `http://localhost:8080` |
| Authorization Server | `http://localhost:9000` |
| Eureka | `http://localhost:8761` |
| AI Model Service | `http://localhost:8005` |
| MLflow | `http://localhost:15000` |
| MinIO Console | `http://localhost:9001` |
| Mailpit UI | `http://localhost:8025` |
| Kafka UI | `http://localhost:18080` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` |

Tắt stack:

```bash
docker compose --env-file .env -f docker-compose.full.yml down
```

Xóa cả volume dữ liệu local khi muốn reset sạch database/model registry:

```bash
docker compose --env-file .env -f docker-compose.full.yml down -v
```

Không commit `.env`, Google client secret, SMTP password, hoặc secret thật.
Nếu cần commit cấu hình mẫu, chỉ cập nhật các file `*.env.example`.

## Chỉ Mục Dữ Liệu Huấn Luyện

Nguồn dữ liệu gốc hiện còn trong repo là
`data/health_insurance_portfolio.csv`. Từ nguồn này, pipeline tạo ra các dataset
đã chuẩn hóa trong `data/generated/` để huấn luyện hai mô hình production:

| File | Vai trò |
| --- | --- |
| `data/generated/frequency_training_dataset.csv` | Dataset huấn luyện Frequency Model, dự báo số claim/kỳ bảo hiểm. |
| `data/generated/severity_training_dataset.csv` | Dataset huấn luyện Severity Model, dự báo chi phí trung bình trên mỗi claim dương. |
| `data/generated/synthetic_insurance_claims.csv` | Master semi-synthetic dataset dùng để đánh giá kết hợp frequency-severity và làm reference dataset cho AI Model Service. |

Hai model hiện tại:

| Model | Script huấn luyện | Target | Thuật toán so sánh |
| --- | --- | --- | --- |
| Frequency Model | `ml/scripts/train_frequency_model.py` | `claim_count` | Negative Binomial GLM và XGBoost Poisson |
| Severity Model | `ml/scripts/train_severity_model.py` | `average_claim_severity` | Gamma GLM và XGBoost Lognormal |

Nhóm feature chính:

| Nhóm feature | Ví dụ thuộc tính | Dùng bởi |
| --- | --- | --- |
| Hồ sơ khách hàng | `age`, `bmi`, `blood_pressure`, `smoker`, `pre_existing_condition`, `exercise_frequency`, `occupation_risk` | Frequency và Severity |
| Thông tin sản phẩm/hợp đồng | `type_policy`, `type_policy_dg`, `type_product`, `reimbursement`, `new_business`, `distribution_channel` | Frequency và Severity |
| Thời gian tham gia | `exposure_time`, `seniority_insured`, `seniority_policy` | Chủ yếu Frequency |
| Lịch sử claim | `prev_claim_count`, `prev_claim_cost`, `prev_average_claim_severity`, `claim_free_years`, `prev_had_claim`, `claim_free_previous_year` | Frequency và Severity |

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

## Kiểm Thử Và Báo Cáo Coverage

Các Spring Boot service chính được kiểm thử bằng JUnit 5, Mockito và JaCoCo.
Mỗi service sinh ra hai nhóm report:

- Surefire report: `target/surefire-reports`
- JaCoCo report: `target/site/jacoco`

Các service hiện có unit test chính:

- `user-service`
- `product-service`
- `payment-service`
- `pricing-service`
- `application-policy-service`
- `notification-service`

Chạy test và sinh JaCoCo report cho một service:

```bash
cd pricing-service
./mvnw org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report
cd ..
```

Chạy test và sinh JaCoCo report cho toàn bộ các service chính:

```bash
for service in \
  user-service \
  product-service \
  payment-service \
  pricing-service \
  application-policy-service \
  notification-service
do
  (cd "$service" && ./mvnw org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report)
done
```

Sau khi chạy test, sinh trang tổng hợp kết quả:

```bash
python3 scripts/generate-service-test-report.py
```

Trang tổng hợp được tạo tại:

```text
test-report/index.html
```

Để xem trang tổng hợp cùng các link JaCoCo chi tiết trong trình duyệt, chạy
static server từ thư mục root của project:

```bash
python3 -m http.server 8080
```

Sau đó mở:

```text
http://localhost:8080/test-report/
```

Dashboard tổng hợp hiển thị số lượng test, số test lỗi, số test bị bỏ qua,
thời gian chạy, coverage theo từng service, coverage theo từng class trong tầng
`service`, và link đến report JaCoCo chi tiết của từng service.

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

Train Frequency Model:

```bash
python3 ml/scripts/train_frequency_model.py --mlflow
```

Train Severity Model:

```bash
python3 ml/scripts/train_severity_model.py --mlflow
```

The unified CLI can run either model type:

```bash
python3 ml/train.py --model-type FREQUENCY --mlflow
python3 ml/train.py --model-type SEVERITY --mlflow
```

Với `--mlflow`, training không chỉ ghi artifact local. Script sẽ log run,
metrics, metadata, report và model artifact lên MLflow Tracking Server
(`http://127.0.0.1:15000` khi train từ host), đồng thời đăng ký model vào
MLflow Model Registry dưới tên:

- `FrequencyModel`
- `SeverityModel`

By default, `--deployment-mode candidate` registers review candidates without
changing production aliases. Use the admin AI model flow or explicit deployment
steps to promote a candidate.

Các file CSV trong `data/generated/` không được upload như dataset artifact mặc
định; chúng là input/reference data để train và để AI Model Service tính baseline
đánh giá candidate. MLflow là nơi lưu model runs, model artifacts và registry
versions.

Evaluate the combined Frequency-Severity model:

```bash
python3 ml/scripts/evaluate_combined_model.py
```

Open the current notebooks:

```bash
jupyter notebook ai-model-service/notebooks/00_combined_pricing_pipeline_eda.ipynb
jupyter notebook ai-model-service/notebooks/02_severity_training_eda.ipynb
jupyter notebook ai-model-service/notebooks/04_frequency_severity_model_comparison.ipynb
```

## Outputs

- Frequency artifact: `ml/artifacts/frequency_model.joblib`
- Frequency metadata: `ml/artifacts/frequency_metadata.json`
- Frequency comparison: `ml/reports/frequency_comparison.csv`
- Frequency report: `ml/reports/frequency_model_report.md`
- Severity artifact: `ml/artifacts/severity_model.joblib`
- Severity metadata: `ml/artifacts/severity_metadata.json`
- Severity comparison: `ml/reports/severity_comparison.csv`
- Severity report: `ml/reports/severity_model_report.md`
- Combined evaluation report: `ml/reports/combined_model_report.md`
- Combined model metadata: `ml/artifacts/combined_model_metadata.json`
