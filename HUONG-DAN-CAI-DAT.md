# Hướng dẫn cài đặt và chạy itjobs trên Windows

Dự án gồm 2 repo chạy cùng nhau:

| Phần | Repo | Cổng |
|---|---|---|
| Backend (Spring Boot, Java 17) | `Roy037/pehome` | 8080 |
| Frontend (React, Vite) | `Roy037/FE-pehome` | 3000 |
| Database (MySQL 8) | cài trên máy | 3306 |

Mac và Linux làm giống hệt, chỉ khác vài lệnh. Xem mục [Mac và Linux](#mac-và-linux) ở cuối.

---

## 1. Cài phần mềm (một lần)

| Phần mềm | Tải ở đâu | Ghi chú |
|---|---|---|
| Git | https://git-scm.com/download/win | |
| JDK **17** | https://adoptium.net (Temurin 17, bản `.msi`) | Khi cài, tick **Set JAVA_HOME variable**. Bản 21 hay 25 không chạy được. |
| Node.js **22 LTS** | https://nodejs.org | Có sẵn npm |
| MySQL **8.4** | https://dev.mysql.com/downloads/mysql/ (bản MSI) | Ghi nhớ mật khẩu `root` đặt lúc cài. Giữ cổng 3306. |
| MySQL Workbench (tùy chọn) | https://dev.mysql.com/downloads/workbench/ | Giao diện xem database |
| Bruno hoặc Postman (tùy chọn) | https://www.usebruno.com | Thử API |

Mở **PowerShell** và kiểm tra:

```powershell
java -version
node -v
git --version
```

`java -version` phải ra `17`. Nếu ra số khác, xem mục [Lỗi thường gặp](#lỗi-thường-gặp).

---

## 2. Lấy code

```powershell
git clone https://github.com/Roy037/pehome.git
git clone https://github.com/Roy037/FE-pehome.git
```

Nếu pull request chưa được merge, chuyển cả hai repo sang nhánh `nam`:

```powershell
git -C pehome checkout nam
git -C FE-pehome checkout nam
```

---

## 3. Tạo database và nhập dữ liệu mẫu

Mở **Command Prompt (cmd)**, không dùng PowerShell cho bước này vì PowerShell không hỗ trợ dấu `<` và làm hỏng tiếng Việt. Vào thư mục `pehome`:

```bat
cd pehome
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p -e "CREATE DATABASE pehome"
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p --default-character-set=utf8mb4 pehome < database\pehome.sql
```

Mỗi lệnh hỏi mật khẩu `root`. Cài MySQL 8.0 thì đổi `8.4` trong đường dẫn thành `8.0`.

**Cách khác, dùng Workbench:** chạy câu `CREATE DATABASE pehome;`, rồi vào **Server → Data Import → Import from Self-Contained File**, chọn `database\pehome.sql`, **Default Target Schema** là `pehome`, bấm **Start Import**.

Lần chạy backend đầu tiên sẽ tự nâng cấp bảng (Flyway V2 đến V18), không cần làm gì thêm.

---

## 4. Cấu hình backend (`pehome\.env`)

**Tạo thư mục lưu file tải lên** (logo, ảnh đại diện, CV), trong cmd:

```bat
mkdir C:\jobhunter-upload\resume C:\jobhunter-upload\company C:\jobhunter-upload\avatar
```

**Tạo file `.env`** từ mẫu:

```bat
copy .env.example .env
```

**Tạo chuỗi bí mật JWT** trong PowerShell, chép kết quả:

```powershell
$b = New-Object byte[] 64; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Mở `.env` bằng Notepad hoặc VS Code. Điền vào các dòng có sẵn (mỗi biến chỉ để **một dòng**, đừng thêm dòng trùng tên):

```
DB_PASSWORD=<mật khẩu root MySQL>
JWT_SECRET=<chuỗi vừa tạo>
FRONTEND_URL=http://localhost:3000
PAYMENT_MOCK=true
```

Rồi thêm một dòng mới (file mẫu chưa có dòng này):

```
UPLOAD_BASE_URI=file:///C:/jobhunter-upload/
```

`PAYMENT_MOCK=true` bật cổng thanh toán giả để thử mua gói mà không cần tài khoản VNPay, ZaloPay hay MoMo. Không dùng khi chạy thật.

**Phần tùy chọn** (để trống thì tính năng đó tắt, app vẫn chạy):

| Tính năng | Biến | Ghi chú |
|---|---|---|
| Gửi email | `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail cùng **App Password** (bật xác minh 2 bước rồi tạo). |
| Đăng nhập Google, Facebook, LinkedIn | `GOOGLE_*`, `FACEBOOK_*`, `LINKEDIN_*` | Redirect URI: `http://localhost:8080/api/v1/auth/oauth/<google\|facebook\|linkedin>/callback` |
| Thanh toán sandbox thật | `VNPAY_*`, `ZALOPAY_*`, `MOMO_*` | Đặt `PAYMENT_MOCK=false` khi dùng |

Không bao giờ commit file `.env`, git đã bỏ qua sẵn. Muốn có khóa giống máy chủ nhóm thì nhờ người giữ khóa gửi riêng.

---

## 5. Chạy backend

Trong PowerShell, ở thư mục `pehome`:

```powershell
.\gradlew.bat bootRun
```

Lần đầu mất vài phút để tải Gradle và thư viện. Chạy xong khi thấy dòng `Started JobhunterApplication`. Kiểm tra bằng cách mở http://localhost:8080/api/v1/jobs?page=1&size=3 trên trình duyệt, thấy JSON là được.

Để cửa sổ này mở trong lúc dùng. Tắt bằng `Ctrl + C`.

---

## 6. Chạy frontend

Mở một cửa sổ PowerShell khác, vào thư mục `FE-pehome`:

```powershell
npm ci
npm run dev
```

Mở http://localhost:3000.

---

## 7. Dùng thử

### Tài khoản mẫu

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Quản trị (SUPER_ADMIN) | `admin@gmail.com` | `123456` |
| Nhà tuyển dụng (HR của VINFAST) | `user@gmail.com` | `123456` |

Tài khoản ứng viên thì tự đăng ký ở `/register`.

**Lưu ý về email:** tài khoản mới đăng ký phải **xác thực email** thì mới ứng tuyển được. Nếu chưa cấu hình Gmail ở bước 4, đánh dấu đã xác thực bằng tay trong MySQL:

```sql
UPDATE users SET email_verified = 1 WHERE email = 'email-vua-dang-ky@example.com';
```

### Ứng viên

1. Đăng ký, xác thực email.
2. Tìm việc ở `/job`, lọc theo kỹ năng, địa điểm, mức lương. Xem công ty ở `/company`.
3. Lưu việc bằng biểu tượng dấu trang.
4. Ứng tuyển ở trang chi tiết việc làm: tải CV (PDF, Word, ODT, RTF hoặc ảnh JPG, PNG, WEBP, tối đa 5 MB) hoặc dán link Google Drive, Dropbox, OneDrive.
5. Hồ sơ cá nhân ở `/ho-so`: thông tin, ảnh đại diện, CV, kỹ năng nhận việc qua email.
6. Mua gói: menu tài khoản → **Gia hạn / nâng cấp**, chọn gói và phương thức thanh toán.

### Nhà tuyển dụng

1. Đăng ký ở `/nha-tuyen-dung/dang-ky` (tài khoản kèm công ty): cần tên công ty, địa chỉ, **mã số thuế**, **số điện thoại**, tick đồng ý **Điều khoản sử dụng dành cho nhà tuyển dụng**.
2. Xác thực email (bấm nút trong email, hoặc xem mục "Lưu ý về email" ở trên nếu chưa cấu hình Gmail).
3. Vào `/admin/company`, bấm sửa công ty, **tải giấy phép kinh doanh** (PDF hoặc ảnh, chỉ quản trị viên xem được). Banner đầu trang liệt kê những mục còn thiếu.
4. Chờ quản trị viên xét duyệt. Quản trị viên chỉ duyệt được khi đã có email xác thực, mã số thuế hợp lệ và giấy phép. Trong lúc chờ, tin của công ty chưa hiển thị công khai.
5. Sau khi được duyệt: đăng tin (tối đa 3 tin đang mở miễn phí), xem hồ sơ ứng tuyển, đổi trạng thái, mời phỏng vấn.
6. Mục **Dịch vụ** (`/admin/dich-vu`): ghim tin lên đầu danh sách, mua thêm chỗ đăng tin, mở khóa kho ứng viên. Với `PAYMENT_MOCK=true` thanh toán bằng cổng giả (nút "Thanh toán thành công").

### Quản trị viên

Vào `/admin` bằng `admin@gmail.com`: duyệt công ty, khóa người dùng, quản lý việc làm, hồ sơ, đánh giá, báo cáo tin, giao dịch, phân quyền.

### Thử thanh toán

- Với `PAYMENT_MOCK=true`: chọn gói, bấm thanh toán, một cửa sổ giả hiện ra với nút thành công hoặc hủy.
- Với VNPay sandbox: chọn **Thẻ nội địa**, ngân hàng **NCB**, số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.

---

## 8. Thử API bằng Bruno hoặc Postman

- **Bruno:** **Open Collection**, chọn thư mục `pehome\bruno`. Góc trên bên phải chọn môi trường `local`.
- **Postman:** **Import**, chọn file `pehome\postman\PolyCareers.postman_collection.json`.

Chạy **Auth → Login** trước. Token được lưu tự động cho các request khác và hết hạn sau 30 phút, gặp lỗi 401 thì đăng nhập lại.

---

## Lỗi thường gặp

| Lỗi | Cách sửa |
|---|---|
| `java -version` không ra 17, hoặc Gradle báo lỗi phiên bản Java | Cài JDK 17 và đặt `JAVA_HOME` trỏ tới nó. Tạm thời trong PowerShell: `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17..."` (đường dẫn thật trên máy bạn). |
| `'mysql' is not recognized` | Dùng đường dẫn đầy đủ tới `mysql.exe` như ở bước 3. |
| `Can't connect to MySQL server` hoặc `Communications link failure` | Dịch vụ MySQL chưa chạy: mở `services.msc`, tìm `MySQL84` (hoặc `MySQL80`), bấm Start. |
| `Access denied for user 'root'` | `DB_PASSWORD` trong `.env` khác mật khẩu root. |
| `Unknown collation: 'utf8mb4_0900_ai_ci'` | Đang dùng MySQL 5.7, cần 8.0 trở lên. |
| Cổng 3306 bị chiếm | Máy có MySQL khác hoặc XAMPP đang chạy. Tắt nó, hoặc dùng luôn MySQL đó. |
| `Flyway validate failed` | Database không tạo từ file dump. Xóa database `pehome`, làm lại bước 3. |
| Tiếng Việt bị lỗi font trong dữ liệu | Nhập lại dump bằng **cmd** với `--default-character-set=utf8mb4`. |
| Frontend trắng trang hoặc báo `Invalid hook call` | Tắt `npm run dev` rồi chạy `npm run dev -- --force`. |
| Trang web không có dữ liệu, đăng nhập báo lỗi mạng | Backend chưa chạy, hoặc chưa chạy xong. |
| Ứng tuyển báo 403 | Tài khoản chưa xác thực email, xem mục 7. |

---

## Mac và Linux

Giống hệt các bước trên, khác ở:

- **Database:** có thể dùng Docker: `docker compose up -d` trong thư mục `pehome`, rồi nhập dữ liệu:
  ```bash
  docker cp database/pehome.sql pehome-mysql:/tmp/pehome.sql
  docker exec pehome-mysql sh -c "mysql -uroot -p12345 --default-character-set=utf8mb4 pehome < /tmp/pehome.sql"
  ```
  Khi đó `DB_PASSWORD=12345`.
- **Tạo file `.env`:** `cp .env.example .env`
- **JWT secret:** `openssl rand -base64 64 | tr -d '\n'`
- **Thư mục upload:** `mkdir -p ~/jobhunter-upload/{resume,company,avatar}` và `UPLOAD_BASE_URI=file:///Users/<tên-máy>/jobhunter-upload/` (Linux: `file:///home/<tên-máy>/jobhunter-upload/`).
- **Chạy backend:** `./gradlew bootRun`. Gặp `Permission denied` thì chạy `chmod +x gradlew` trước.
