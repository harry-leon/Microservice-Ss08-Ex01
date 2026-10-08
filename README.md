# Bài tập 1 - Cài đặt Circuit Breaker cơ bản (Ngắt mạch khi sập nguồn)

## 1. Mục tiêu
- **Kiến thức**: Hiểu cơ chế hoạt động của Circuit Breaker (Lesson 01 & 02).
- **Kỹ năng**: Cấu hình trạng thái đóng/mở mạch khi Service gọi nhau thất bại.

## 2. Mô tả chi tiết
- **Bối cảnh**: `Appointment-Service` (port `8083`) gọi sang `Doctor-Service` (port `8082`) để kiểm tra thông tin/lịch trực của bác sĩ. Nếu `Doctor-Service` bị sập hoặc gặp sự cố, chúng ta không muốn `Appointment-Service` phải chờ đợi vô ích dẫn đến nghẽn luồng tài nguyên (cascading failure).
- **Giải pháp**: Tích hợp **Resilience4j Circuit Breaker** vào `Appointment-Service` với instance cấu hình là `doctorServiceCB`. Khi tỷ lệ gọi thất bại vượt ngưỡng cấu hình, mạch sẽ chuyển từ `CLOSED` sang `OPEN` và lập tức từ chối gọi tiếp (Fast-Fail), ngắt mạch bảo vệ hệ thống.

## 3. Cấu hình Resilience4j (`application.properties`)
```properties
# Resilience4j Circuit Breaker Configuration
resilience4j.circuitbreaker.instances.doctorServiceCB.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.doctorServiceCB.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.doctorServiceCB.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.doctorServiceCB.permitted-number-of-calls-in-half-open-state=3
resilience4j.circuitbreaker.instances.doctorServiceCB.sliding-window-size=10
resilience4j.circuitbreaker.instances.doctorServiceCB.sliding-window-type=COUNT_BASED

# Resilience4j Logging
logging.level.io.github.resilience4j=DEBUG
```

### Ý nghĩa các thông số:
- `failure-rate-threshold=50`: Nếu tỷ lệ lỗi >= 50% thì chuyển mạch sang trạng thái OPEN.
- `minimum-number-of-calls=5`: Số lượng request tối thiểu cần đạt trong cửa sổ trượt trước khi tính toán tỷ lệ lỗi.
- `wait-duration-in-open-state=10s`: Thời gian mạch ở trạng thái OPEN trước khi chuyển sang HALF-OPEN để thử nghiệm lại.
- `permitted-number-of-calls-in-half-open-state=3`: Số lượng request được phép thử ở trạng thái HALF-OPEN.
- `logging.level.io.github.resilience4j=DEBUG`: Bật log chi tiết sự kiện thay đổi trạng thái mạch của Resilience4j.

## 4. Hướng dẫn chạy thử nghiệm (Verify Circuit Breaker)

### Bước 1: Khởi động Discovery Server & Doctor-Service (nếu có)
- Chạy Discovery Server (port `8761`) và Doctor-Service (port `8082`).

### Bước 2: Khởi động Appointment-Service
```bash
mvn spring-boot:run
```
Service sẽ lắng nghe tại cổng `8083`.

### Bước 3: Tắt Doctor-Service để mô phỏng sập nguồn
- Dừng `Doctor-Service` (port `8082`).

### Bước 4: Gửi liên tiếp 5 request tạo lịch hẹn
Gửi 5 request POST đến `http://localhost:8083/api/v1/appointments`:
```json
{
  "patientId": 1,
  "doctorId": 1,
  "reason": "General checkup",
  "status": "PENDING"
}
```

### Bước 5: Quan sát Log và Trạng thái Circuit Breaker
Khi gọi đến lần thứ 5 và thất bại, Resilience4j sẽ ghi nhận:
```
CircuitBreaker 'doctorServiceCB' exceeded failure rate threshold. Current failure rate: 100.0%
CircuitBreaker 'doctorServiceCB' changed state from CLOSED to OPEN
```
Ở các lượt gọi tiếp theo khi mạch đang `OPEN`, hệ thống lập tức ném ngoại lệ `CallNotPermittedException` (ngắt mạch ngay lập tức mà không cần tốn thời gian chờ timeout sang service đích).
Sau `10s` (hết thời gian `wait-duration-in-open-state`), mạch sẽ chuyển sang `HALF-OPEN` để thử lại các request tiếp theo.
