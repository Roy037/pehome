# 🚀 Java Spring RESTful APIs - Xây Dựng Backend với Spring Boot

> **Cài đặt và chạy trên Windows (kèm Mac, Linux):** xem [HUONG-DAN-CAI-DAT.md](HUONG-DAN-CAI-DAT.md).

This is the **starter project** for the **Java Spring RESTful APIs - Xây Dựng Backend với Spring Boot** course by **Hỏi Dân IT**.

---

## 📢 IMPORTANT NOTICE  
This source code is provided **exclusively for enrolled students** in this course.  
❌ **DO NOT UPLOAD this code to GitHub (Public), GitLab, or any other public repository.**  
❌ **DO NOT SHARE this project on forums, Telegram, Discord, or social media.**  
✅ You **can use Git** for personal learning, but **your repository must be PRIVATE**.

💡 **Violators may face:**  
- DMCA takedown requests.  
- Account suspension on learning platforms.  
- Legal action in serious cases.  

📩 For inquiries, contact: **admin@hoidanit.vn**

---

## 📖 How to Use This Project?

===
Môi trường chạy dự án: Java 17

### Cấu hình bí mật (bắt buộc trước khi chạy)
`application.properties` không còn chứa mật khẩu hay secret. Sao chép `.env.example` thành `.env` (đã được git bỏ qua) rồi điền:

| Biến | Ý nghĩa |
| --- | --- |
| `DB_PASSWORD` | Mật khẩu root MySQL (khớp `MYSQL_ROOT_PASSWORD` trong `docker-compose.yml`) |
| `JWT_SECRET` | Chuỗi base64 dùng ký JWT, tạo bằng `openssl rand -base64 64 \| tr -d '\n'` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Tài khoản Gmail và **mật khẩu ứng dụng** (bật xác minh 2 bước rồi tạo App Password), dùng để gửi email quên mật khẩu, thông báo trạng thái hồ sơ / lịch phỏng vấn và bản tin việc làm hằng tuần (Thứ Hai 08:00 giờ Việt Nam; đặt `JOB_ALERT_CRON=-` để tắt lịch, hoặc SUPER_ADMIN gọi `POST /api/v1/subscribers/send-digest`) |
| `FRONTEND_URL` | Địa chỉ frontend dùng tạo liên kết đặt lại mật khẩu (mặc định `http://localhost:3000`) |

**Email.** Mọi email dùng chung một khung (logo itjobs đính kèm ngay trong thư vì Gmail không hiện SVG hay ảnh từ localhost, chân trang thương hiệu) gồm `templates/fragments.html` và mẫu chung `templates/notice.html`; nội dung nằm ở `NotificationService`, trạng thái hồ sơ ở `ResumeService`, bản tin ở `JobAlertService`. Danh sách:
- Tài khoản: chào mừng kèm nút **xác thực email** khi đăng ký (ứng viên và nhà tuyển dụng; liên kết 48 giờ, chỉ lưu SHA-256, mở lại liên kết đã dùng vẫn báo thành công), link đặt lại mật khẩu, báo mật khẩu vừa đổi. Tài khoản đăng ký công khai bắt đầu ở trạng thái chưa xác thực (cột `users.email_verified`, V17; mọi tài khoản có sẵn coi như đã xác thực): vẫn xem và dùng được site nhưng **chưa ứng tuyển và chưa đăng ký nhận việc qua email** (403) cho tới khi xác thực, có thanh nhắc "Gửi lại email" (`POST /api/v1/auth/resend-verification`, cách nhau 1 phút, 429). Đặt lại mật khẩu thành công cũng tính là xác thực. Trang đích của nút: `/xac-thuc-email?token=` gọi `POST /api/v1/auth/verify-email`. Đổi mật khẩu khi đã đăng nhập: mục "Đổi mật khẩu" trong menu tài khoản (cả giao diện khách lẫn quản trị) gọi `POST /api/v1/auth/change-password` (sai mật khẩu hiện tại tính chung giới hạn 8 lần / 15 phút với đăng nhập; đổi xong mọi phiên đăng xuất và có email báo).
- Ứng tuyển: biên nhận đã gửi hồ sơ (ứng viên), báo hồ sơ mới kèm thư giới thiệu (mọi tài khoản HR của công ty), vào danh sách rút gọn, mời / đổi lịch phỏng vấn, được nhận, từ chối. Thư từ chối gợi ý tối đa 3 việc đang tuyển trùng nhiều kỹ năng nhất (bỏ việc đã ứng tuyển) và nút tìm việc lọc sẵn theo kỹ năng.
- Công ty: được duyệt, bị từ chối kèm góp ý. Mọi email gửi nhà tuyển dụng chỉ tới các tài khoản HR **đã xác thực email**.
- Nhà tuyển dụng: tóm tắt hồ sơ mới mỗi sáng (08:30, thay cho một email mỗi hồ sơ; `HR_DIGEST_CRON`), tin tuyển dụng sắp hết hạn trong 24 đến 48 giờ tới (10:00; `JOB_EXPIRY_REMINDER_CRON`), tin bị admin khóa kèm lý do và khi được mở khóa, công ty có đánh giá mới (chỉ lần đánh giá đầu của mỗi người).
- Ứng viên: việc làm đã lưu sắp hết hạn nộp, chưa ứng tuyển (09:30; `SAVED_JOB_REMINDER_CRON`; chỉ tài khoản đã xác thực), nhắc lịch phỏng vấn trước 1 ngày gửi cả ứng viên lẫn HR kèm link họp (mỗi giờ; `INTERVIEW_REMINDER_CRON`), tài khoản bị khóa / mở khóa.
- Các email theo lịch xét một cửa sổ thời gian đúng bằng chu kỳ chạy nên mỗi tin, buổi phỏng vấn hay hồ sơ chỉ rơi vào một lượt và không cần lưu cờ "đã gửi"; đổi lại, lượt nào app đang tắt thì không được bù. Đặt mỗi biến cron thành `-` để tắt từng loại.
- Newsletter: xác nhận đăng ký nhận việc làm, bản tin hằng tuần (`JOB_ALERT_CRON`, mặc định 08:00 thứ Hai). Bản tin có liên kết "Hủy nhận bản tin" và header `List-Unsubscribe` + `List-Unsubscribe-Post` (hủy bằng một cú bấm ngay trong Gmail); token là HMAC ký bằng `JWT_SECRET`, không hết hạn, trỏ tới `POST /api/v1/subscribers/unsubscribe?token=...`. Muốn nút của Gmail hoạt động khi chạy thật, đặt `BACKEND_URL` là địa chỉ HTTPS công khai của API.
- Báo cho admin (`ADMIN_ALERT_EMAIL`, nhiều địa chỉ cách nhau dấu phẩy, bỏ trống = không gửi): có công ty mới chờ duyệt, công ty bị từ chối đã sửa hồ sơ và gửi duyệt lại, ứng viên báo cáo tin tuyển dụng. Không tự lấy email tài khoản admin để gửi, vì `admin@gmail.com` của dữ liệu mẫu là địa chỉ Gmail thật của người khác.
- Thanh toán: biên nhận khi đơn chuyển sang đã thanh toán, nhắc gia hạn khi gói cuối cùng còn 3 ngày (`PLAN_REMINDER_CRON`, mặc định 09:00 hằng ngày; đặt `-` để tắt). Nút "Gia hạn ngay" mở `FRONTEND_URL/?goi=1`, frontend tự bật popup gói.
- Thẻ việc làm trong thư có logo công ty đính kèm nếu file logo upload tồn tại trong `UPLOAD_BASE_URI/company/`, nếu không thì hiện chữ viết tắt tên công ty.
- Logo itjobs và logo công ty trong thư: khi `BACKEND_URL` là địa chỉ HTTPS công khai thì logo được **liên kết** (`<BACKEND_URL>/mail/itjobs-logo.png`, `<BACKEND_URL>/storage/company/...`, đúng cách các dịch vụ gửi thư lớn làm, hiện ngay ở hộp thư đến); khi chạy local (http) logo được **đính kèm trong thư** (`cid:`) vì không có địa chỉ công khai nào để liên kết. Gmail không vẽ ảnh đính kèm trong thư mục **Spam** (chỉ liệt kê như tệp), bấm "Not spam" thì logo hiện. Mỗi thư có thêm bản văn bản thuần (`text/plain`) bên cạnh HTML.

Muốn thử mà không gửi mail thật, trỏ `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_SMTP_AUTH=false`, `MAIL_STARTTLS=false` tới một SMTP giả như Mailpit hoặc Mailtrap (xem `.env.example`).

Cũng có thể đặt các biến này trong Environment variables của run configuration (IntelliJ). Thiếu `JWT_SECRET` thì ứng dụng dừng ngay lúc khởi động và báo rõ nguyên nhân. Trên macOS nhớ đặt thêm `UPLOAD_BASE_URI=file:///Users/<ban>/jobhunter-upload/`.

CV là tệp riêng tư: không có URL công khai, chỉ xem được qua `GET /api/v1/resumes/{id}/document` (ứng viên nộp đơn, nhà tuyển dụng của công ty đăng tin, hoặc SUPER_ADMIN) và `GET /api/v1/me/profile/cv` (CV trong hồ sơ của chính mình). Chỉ logo công ty và ảnh đại diện được phục vụ công khai tại `/storage/company/**` và `/storage/avatar/**`.

Hồ sơ ứng tuyển nhận tệp **≤ 5 MB** định dạng PDF, Word (DOC, DOCX), ODT, RTF hoặc ảnh JPG, PNG, WEBP (kiểm tra cả phần đầu tệp, không chỉ đuôi; không nhận SVG). PDF và ảnh xem được ngay trên trang, các tệp Word, ODT, RTF được tải về hoặc liên kết https của Google Drive / Dropbox / OneDrive, kèm thư xin việc tùy chọn. Trang công ty có banner, website, mạng xã hội (mỗi liên kết phải đúng tên miền của mạng đó), loại hình Product/Outsource và bản đồ: ô bản đồ chỉ chấp nhận URL **nhúng** của Google Maps (Chia sẻ > Nhúng bản đồ), vì giá trị này được hiển thị trong `iframe`. Ứng viên báo cáo tin tuyển dụng qua `POST /api/v1/me/job-reports`; SUPER_ADMIN xử lý ở trang admin "Báo cáo tin" (xem bên dưới).

**Quy tắc phân quyền cần biết.** Tài khoản nhà tuyển dụng (gắn với một công ty) chỉ dùng công cụ tuyển dụng: không ứng tuyển, lưu tin, theo dõi hay đánh giá công ty (API trả 403). Ảnh logo/banner công ty chỉ nhà tuyển dụng và admin được tải lên. Công ty chưa được duyệt không xuất hiện công khai (chỉ admin và chính nhà tuyển dụng của nó thấy). Mỗi người chỉ đăng ký nhận việc làm qua email cho địa chỉ của chính mình, và tắt công tắc thông báo trong hồ sơ sẽ hủy đăng ký đó. Đăng nhập sai 8 lần trong 15 phút thì tài khoản bị khóa tạm thời (429). Tham số `filter` và `sort` bị từ chối (400) nếu nhắc tới `password`, `refreshToken`, `tokenHash` hoặc quan hệ `users`, vì các bộ lọc này có thể bị dùng để dò mật khẩu hay token từng ký tự. Hạn nộp hồ sơ tính hết ngày được chọn (23:59:59), không phải đầu ngày.

Mức lương của tin tuyển dụng là một khoảng: `salary` là mức tối thiểu và `salaryMax` là mức tối đa (tùy chọn); `salary` = 0 và không có `salaryMax` nghĩa là "thỏa thuận". Bộ lọc lương ở trang việc làm là thanh trượt 0–100 triệu (đầu 100 nghĩa là không giới hạn) và lọc theo khoảng giao nhau, tin thỏa thuận chỉ hiện khi tích "Gồm cả tin lương thỏa thuận".

**Kiểm duyệt tin tuyển dụng.** Ứng viên báo cáo tin qua `POST /api/v1/me/job-reports`; SUPER_ADMIN xem hàng đợi ở trang admin "Báo cáo tin" (`GET /api/v1/job-reports`), khóa tin kèm lý do (`PUT /api/v1/jobs/{id}/lock`), mở khóa (`PUT /api/v1/jobs/{id}/unlock`) hoặc bỏ qua báo cáo (`DELETE /api/v1/job-reports/{id}`). Tin bị khóa biến mất khỏi mọi danh sách và trang chi tiết công khai (404), không nhận hồ sơ mới, không có trong email bản tin và danh sách việc đã lưu; chỉ admin và công ty sở hữu mới còn thấy (kèm lý do). Nhà tuyển dụng sửa được nội dung nhưng không tự mở khóa được.

**Chia sẻ tin tuyển dụng.** Nút chia sẻ ở trang việc làm và trang công ty mở một cửa sổ chọn kênh (Facebook, Zalo, LinkedIn, X, Email, Messenger, sao chép link, mã QR). Link chia sẻ trỏ tới `GET /share/job/{id}` hoặc `GET /share/company/{id}` của backend (công khai, không cần đăng nhập): trang này chứa thẻ `og:title`, `og:description`, `og:image` riêng cho từng tin để Facebook, LinkedIn, Zalo hiện thẻ xem trước đúng, rồi chuyển người mở sang trang web. Tin bị khóa hoặc của công ty chưa duyệt chỉ chuyển về trang chủ, không lộ nội dung. Thẻ xem trước chỉ hiện khi `BACKEND_URL` là địa chỉ công khai (https), trên localhost các mạng xã hội không truy cập được. Zalo không có link chia sẻ web nếu không có tài khoản OA, nên trên điện thoại dùng bảng chia sẻ của máy, trên máy tính dùng mã QR.

**Xác minh nhà tuyển dụng và điều khoản.** Đăng ký nhà tuyển dụng cần mã số thuế (10 chữ số hoặc 13 chữ số dạng `0123456789-001`, không trùng), số điện thoại công ty, website (tùy chọn) và `acceptTerms=true` (đồng ý Điều khoản sử dụng dành cho nhà tuyển dụng, lưu `users.terms_version` và `terms_accepted_at`; `POST /api/v1/me/terms` để tài khoản cũ đồng ý phiên bản mới, đăng tin cần đã đồng ý). Giấy phép kinh doanh tải lên thư mục riêng tư `company-doc` (PDF hoặc ảnh, `POST /api/v1/files`), không có đường dẫn công khai: chỉ quản trị viên và nhà tuyển dụng của chính công ty đó đọc được qua `GET /api/v1/companies/{id}/license`. `GET /api/v1/companies/{id}/verification` trả bảng kiểm cho quản trị viên (email người liên hệ đã xác thực, mã số thuế hợp lệ và không trùng, đã có giấy phép, có số điện thoại, email cùng tên miền với website, kèm cảnh báo email miễn phí). `PUT /companies/{id}/approve` bị từ chối (400, kèm danh sách) khi thiếu email xác thực, mã số thuế hoặc giấy phép. Công ty đã duyệt mà đổi tên, mã số thuế hoặc giấy phép vẫn hiển thị nhưng quản trị viên nhận email báo kiểm tra lại. Tin của công ty chưa duyệt (hoặc bị từ chối) không hiện ở danh sách, trang chi tiết, email nhắc việc và không nhận hồ sơ; chỉ nhà tuyển dụng của công ty đó và quản trị viên thấy. Công ty có từ trước và công ty tạo bởi quản trị viên giữ trạng thái đã duyệt.

**Gói Premium cho ứng viên (thanh toán VNPay / ZaloPay / MoMo).** Ba gói trả một lần, dùng 30 ngày, không tự gia hạn: Basic 49.000đ (5 kỹ năng nhận việc qua email, 50 việc làm đã lưu), Standard 99.000đ (10 kỹ năng, 100 việc đã lưu, hồ sơ được đánh dấu Premium với nhà tuyển dụng), Premium 149.000đ (không giới hạn thực tế). Gói miễn phí: 3 kỹ năng, 20 việc đã lưu. Giá và hạn mức nằm trong `PlanEnum`. Mua lại cùng gói thì thời hạn nối tiếp, nâng cấp có hiệu lực ngay. Luồng: `POST /api/v1/me/orders` trả về `paymentUrl` -> trình duyệt sang VNPay -> quay về trang chủ `FRONTEND_URL/` kèm kết quả trên địa chỉ -> frontend tự gọi `GET /api/v1/payments/vnpay-return` để backend kiểm tra chữ ký HMAC-SHA512 và số tiền rồi mới kích hoạt gói (xử lý lặp lại an toàn), hiện kết quả trong một popup rồi đưa người dùng về đúng trang họ vừa rời đi. VNPay cũng gọi server-to-server tới `GET /api/v1/payments/vnpay-ipn`; khi chạy thật hãy khai báo URL công khai này trong cổng merchant (local không nhận được IPN nhưng trang return vẫn kích hoạt gói).
- **VNPay sandbox:** đăng ký merchant tại [VNPay Sandbox](https://sandbox.vnpayment.vn/devreg/), lấy `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET`, điền vào `.env`, đặt `PAYMENT_MOCK=false`, rồi khởi động lại backend. Endpoint mặc định là `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`. Return URL là `FRONTEND_URL/` (local: `http://localhost:3000/`); đăng ký IPN URL HTTPS công khai `https://<backend>/api/v1/payments/vnpay-ipn` với VNPay. Backend kiểm tra chữ ký HMAC-SHA512, đúng `vnp_TmnCode`, mã đơn, phương thức, số tiền nhân 100 và cả hai trạng thái thành công trước khi cấp gói. Đơn MoMo/ZaloPay không được xác nhận qua VNPay. Return và IPN lặp lại không cộng thêm hạn. [Tài liệu chính thức và dữ liệu thử nghiệm VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- **Thẻ thử nghiệm (chỉ dùng trong sandbox, không phải thẻ thật):**
  - VNPay: chọn "Thẻ nội địa và tài khoản ngân hàng", ngân hàng **NCB**, số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, mật khẩu OTP `123456`.
  - MoMo (theo [tài liệu MoMo](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions/)): ví thử nghiệm mật khẩu `000000`, OTP `0000` hoặc `000000`; thẻ ATM `9704 0000 0000 0018` hạn `03/07` (thành công), `...0026` (thẻ khóa), `...0034` (không đủ tiền), `...0042` (vượt hạn mức); thẻ tín dụng `5200 0000 0000 1096` hạn `05/26` CVC `111` (thành công), `5200 0000 0000 1104` (thất bại), `4111 1111 1111 1111` hạn `05/26` CVC `111` (thành công, không OTP).
  - ZaloPay: dùng app ZaloPay Sandbox hoặc thẻ thử nghiệm được nêu trong [tài liệu ZaloPay](https://docs.zalopay.vn/); thông tin này do ZaloPay thay đổi nên không chép lại ở đây.
- **Thử không cần tài khoản:** đặt `PAYMENT_MOCK=true` (chỉ để phát triển) thì thay vì sang cổng thanh toán sẽ hiện một popup cổng giả lập với hai nút "thành công" / "hủy", luồng sau đó giống hệt (chữ ký vẫn được kiểm tra). Không bao giờ bật trong môi trường thật.
- **Phương thức thanh toán:** popup có 4 lựa chọn: VNPay, ZaloPay, MoMo, Ngân hàng. VNPay và Ngân hàng đi qua VNPay; ZaloPay và MoMo dùng cổng riêng. `GET /api/v1/payments/methods` trả trạng thái theo cấu hình từng cổng; gửi `method` khi tạo đơn, mặc định VNPAY. Các enum VietQR/Thẻ vẫn được giữ để đọc lịch sử đơn cũ.
- **ZaloPay sandbox:** thêm `ZALOPAY_APP_ID`, `ZALOPAY_KEY1`, `ZALOPAY_KEY2` của merchant sandbox vào `.env`, đặt `PAYMENT_MOCK=false`, rồi khởi động lại backend. Endpoint mặc định là `https://sb-openapi.zalopay.vn/v2`; không cần cấu hình VNPay để dùng ZaloPay. Thiếu khóa thì lựa chọn ZaloPay bị vô hiệu hóa. Đơn được ký HMAC-SHA256 bằng Key1; `app_trans_id` có tiền tố ngày `yyMMdd` theo GMT+7; số tiền gửi trực tiếp bằng VND; đơn có thời hạn 15 phút.
  - Backend tạo đơn `/v2/create` và trả `order_url`; frontend chuyển sang trang ZaloPay sandbox. `embed_data.redirecturl` đưa về `FRONTEND_URL/?payment=zalopay&txnRef=...`. Frontend chờ đăng nhập và gọi `GET /api/v1/me/orders/zalopay-result?txnRef=...`; chỉ chủ đơn được tra cứu. Backend gọi `/v2/query` với Key1, kiểm tra số tiền và mã giao dịch trước khi kích hoạt gói. Tham số `status` trên URL không được dùng để xác nhận thanh toán.
  - Callback: `POST /api/v1/payments/zalopay-callback`. Backend kiểm tra HMAC bằng Key2 trên nguyên chuỗi `data`, kiểm tra App ID, người dùng, phương thức và số tiền; khóa hàng đơn để callback/tra cứu đồng thời không kích hoạt hai lần. Phản hồi callback theo định dạng ZaloPay, không bọc trong `RestResponse`.
  - Trên localhost có thể để `ZALOPAY_CALLBACK_URL` trống: trang quay về vẫn tra cứu trực tiếp. Backend cũng tra cứu tối đa 20 đơn PENDING được tạo trong 24 giờ gần nhất mỗi phút (bỏ qua đơn dưới 1 phút), phục hồi giao dịch khi trình duyệt đóng hoặc callback bị mất. Đặt `ZALOPAY_RECONCILE_CRON=-` để tắt lịch này. Nếu ZaloPay chưa xác nhận, popup hiện trạng thái chờ và nút “Kiểm tra lại” cho cùng đơn, không tạo đơn mới.
  - Để nhận callback trực tiếp, đặt `ZALOPAY_CALLBACK_URL=https://<backend-cong-khai>/api/v1/payments/zalopay-callback` hoặc đăng ký URL đó trong merchant portal; ZaloPay không gọi được localhost. Dùng tài khoản/app sandbox theo hướng dẫn của ZaloPay.
  - Tài liệu chính thức: [Create Order](https://docs.zalopay.vn/docs/specs/order-create/), [Query Order Status](https://docs.zalopay.vn/docs/specs/order-query/), [Callback](https://docs.zalopay.vn/docs/specs/callback-api/).
- **MoMo sandbox:** đăng ký/đăng nhập [MoMo for Business](https://business.momo.vn/), lấy khóa môi trường TEST trong phần tích hợp: `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`. Điền vào `.env`, đặt `PAYMENT_MOCK=false` rồi khởi động lại backend. Endpoint mặc định là `https://test-payment.momo.vn/v2/gateway/api`; luồng dùng `captureWallet`, `autoCapture=true` và số tiền VND.
  - Đặt `MOMO_IPN_URL=https://<backend-cong-khai>/api/v1/payments/momo-ipn` để nhận thông báo thanh toán. Mặc định dùng `BACKEND_URL/api/v1/payments/momo-ipn`; MoMo không thể gọi localhost. Khi chưa có địa chỉ công khai, frontend vẫn có thể tra cứu khi người dùng quay lại, và lịch đối soát vẫn chạy nếu backend đang bật.
  - MoMo đưa trình duyệt về `FRONTEND_URL/?payment=momo&txnRef=...`. Frontend chờ đăng nhập, gọi `GET /api/v1/me/orders/momo-result?txnRef=...`; backend kiểm tra chủ đơn, gọi API `/query` có chữ ký và kiểm tra merchant/mã yêu cầu/mã đơn/số tiền. Tham số kết quả trên địa chỉ trình duyệt không tự cấp gói.
  - IPN được kiểm tra HMAC-SHA256 trước khi thay đổi đơn. Trả HTTP 204 với body trống khi nhận hợp lệ. Chỉ `resultCode=0` cùng mã giao dịch hợp lệ mới cấp gói; đang xử lý/đã ủy quyền thì tiếp tục chờ. Các lỗi cuối cùng như hết hạn hoặc người dùng hủy chuyển sang FAILED; sự cố kết nối không bị coi là đã thanh toán hay đã hủy. Callback trùng chỉ ghi nhận một lần.
  - Backend tra cứu tối đa 20 đơn PENDING trong 24 giờ gần nhất mỗi phút, bỏ qua đơn dưới 1 phút. Đặt `MOMO_RECONCILE_CRON=-` để tắt. Trong popup dùng “Kiểm tra lại” để hỏi trạng thái cùng đơn. MoMo yêu cầu timeout API tối thiểu 30 giây; lúc chờ API, backend không giữ khóa hàng cản IPN.
  - Cần [MoMo Test App và ví thử nghiệm](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions/) để quét QR sandbox. [Tạo đơn](https://developers.momo.vn/v3/docs/payment/api/wallet/onetime/), [tra cứu](https://developers.momo.vn/v3/docs/payment/api/payment-api/query/), [IPN](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/).
- Người đã có giao dịch không xóa được tài khoản (409). SUPER_ADMIN xem sổ giao dịch ở `GET /api/v1/orders`.
- **Khóa tài khoản.** SUPER_ADMIN khóa/mở khóa người dùng ở trang admin "Người dùng" (`PUT /api/v1/users/{id}/lock` và `/unlock`, quyền `USERS`). Tài khoản bị khóa không đăng nhập được (chỉ người nhập đúng mật khẩu mới thấy lý do), refresh token bị xóa và token đang giữ bị từ chối ở mọi API (401, giao diện tự đăng xuất). Không khóa được tài khoản quản trị viên hay chính mình. Khóa không ẩn tin tuyển dụng hay công ty của nhà tuyển dụng đó; việc đó vẫn do kiểm duyệt tin xử lý.
- **Duyệt nhà tuyển dụng.** Công ty tự đăng ký có ba trạng thái: chờ duyệt, đã duyệt và bị từ chối (chưa duyệt kèm lý do, `PUT /api/v1/companies/{id}/reject`). Công ty bị từ chối vẫn ẩn như công ty chờ duyệt; nhà tuyển dụng thấy lý do ở banner trang quản trị, sửa và lưu thông tin công ty thì công ty quay về hàng chờ (admin sửa thì không). Duyệt (`/approve`) xóa lý do từ chối.
- **Kho ứng viên.** Ứng viên bật "Cho phép nhà tuyển dụng tìm thấy hồ sơ" ở trang hồ sơ (mặc định tắt). Chỉ SUPER_ADMIN và nhà tuyển dụng của công ty **đã duyệt** dùng được `GET /api/v1/talents` (tìm theo từ khóa, cấp độ, kinh nghiệm, lĩnh vực, ngành nghề, kỹ năng), `/talents/{id}` (email, mục tiêu, kinh nghiệm, kỹ năng) và `/talents/{id}/cv`. Không bao giờ trả về người tham khảo; tài khoản bị khóa hoặc đã tắt chia sẻ trả 404. Tìm kiếm dùng tham số riêng, không nhận `filter`/`sort` tự do, nên không thể dò các trường ứng viên không chia sẻ. Trang admin "Kho ứng viên" ẩn với công ty chưa được duyệt.
- **Hồ sơ ứng viên** (`/ho-so` → "Thông tin cơ bản") sửa được cả tuổi (14 đến 100), giới tính và địa chỉ của tài khoản, không chỉ họ tên, chức danh, cấp bậc: các trường này lưu trên bảng `users` qua `PUT /api/v1/me/profile` và hiện thành thẻ nhỏ ở đầu hồ sơ. Tài khoản tạo bằng Google / Facebook / LinkedIn bắt đầu với các trường này trống.
- **Bảng điều khiển (admin / nhà tuyển dụng)**: góc phải có ảnh đại diện; menu của nó có "Ảnh đại diện" (đổi hoặc xóa ảnh, gọi `PUT /api/v1/me/profile/avatar`, dùng được cho mọi tài khoản đã đăng nhập vì nhà tuyển dụng và admin không có trang hồ sơ ứng viên), "Đổi mật khẩu" và "Đăng xuất" (luôn kết thúc phiên trên máy dù máy chủ trả lỗi). Cột "Trạng thái" của danh sách việc làm cho biết tin thật sự đang ở đâu: Đang tuyển / Hẹn đăng (kèm ngày bắt đầu) / Đã hết hạn / Tạm ẩn / Bị khóa. Trang công khai chỉ hiện tin Đang tuyển, nên tin có ngày bắt đầu ở tương lai sẽ chưa hiện cho tới ngày đó.
- **Trang admin "Giao dịch"** (`/admin/order`, quyền ORDERS): sổ giao dịch Premium chỉ để xem, lọc theo mã giao dịch, email người mua, gói, phương thức, trạng thái; không có sửa hay xóa vì giao dịch là bút toán. Tin tuyển dụng, kỹ năng (tab "Kỹ năng" trong trang Việc làm), công ty, người dùng, hồ sơ ứng tuyển (có nút xóa cho ai có quyền DELETE, tức SUPER_ADMIN), đánh giá, việc đã lưu, đăng ký nhận tin, quyền hạn và vai trò đều đủ thêm / sửa / xóa.
- Trang Tổng quan của SUPER_ADMIN có doanh thu (tổng, 30 ngày gần nhất, theo từng gói), số gói đang còn hạn, tỉ lệ ứng viên/nhà tuyển dụng và biểu đồ 6 tháng (đăng ký, hồ sơ, doanh thu theo giờ Việt Nam), lấy từ `GET /api/v1/admin/stats` (quyền `STATS`). Chỉ đơn đã thanh toán (PAID) mới tính vào doanh thu.

**Đăng nhập bằng Google, Facebook, LinkedIn.** Luồng Authorization Code chạy ở backend (`vn.hoidanit.jobhunter.oauth`), nên client secret không bao giờ ra trình duyệt. Nút trên form đăng nhập / đăng ký chỉ bật khi có đủ `*_CLIENT_ID` và `*_CLIENT_SECRET` trong `.env` (chưa có thì nút mờ). Đăng ký ứng dụng ở từng nhà cung cấp và khai báo **redirect URI** là `<BACKEND_URL>/api/v1/auth/oauth/<google|facebook|linkedin>/callback` (local: `http://localhost:8080/api/v1/auth/oauth/google/callback`):
- **Google** (console.cloud.google.com → APIs & Services → Credentials → OAuth client ID, loại Web application; màn hình đồng ý OAuth cần scope `openid email profile`).
- **Facebook** (developers.facebook.com → Create app → Facebook Login; thêm redirect URI vào "Valid OAuth Redirect URIs"; quyền `email`, `public_profile`). Tài khoản Facebook không có email thì không đăng nhập được (báo "chưa chia sẻ email"). Chế độ Development chỉ cho tài khoản được thêm làm tester.
- **LinkedIn** (linkedin.com/developers → Create app → thêm sản phẩm "Sign In with LinkedIn using OpenID Connect"; scope `openid profile email`).

Cách hoạt động và các quy tắc an toàn:
- Người dùng bấm nút -> `GET /api/v1/auth/oauth/{provider}/authorize` chuyển sang nhà cung cấp kèm `state` ký HMAC (hạn 10 phút, chứa trang cần quay lại) và một cookie `oauth_state` ràng buộc với đúng trình duyệt đó; `.../callback` kiểm tra cả hai rồi mới đổi code lấy token và đọc hồ sơ. Sai state, sai cookie, hay trang quay lại không phải đường dẫn trong site đều bị từ chối (về `/login?oauth_error=failed`). Backend phát cookie `refresh_token` rồi chuyển tới `/dang-nhap-xong`, trang này đổi cookie thành access token như khi tải lại trang.
- Chỉ **tài khoản ứng viên** (vai trò NORMAL_USER, không gắn công ty) đăng nhập kiểu này; nhà tuyển dụng và admin vẫn dùng email + mật khẩu (`unsupported_account`). Tài khoản bị khóa bị từ chối.
- Tài khoản đã liên kết thì vào thẳng. Chưa liên kết mà trùng email với một tài khoản có sẵn thì liên kết vào đó, **nhưng chỉ khi nhà cung cấp xác nhận email** (Facebook không có cờ này nên coi "có email" là đã xác nhận). Nếu tài khoản có sẵn chưa xác thực email, mật khẩu của nó bị thay bằng mật khẩu ngẫu nhiên và phiên cũ bị hủy trước khi liên kết, để người đăng ký trước bằng email của người khác không giữ được đường vào. Email chưa ai dùng thì tạo tài khoản ứng viên mới đã xác thực, gửi email chào mừng, mật khẩu ngẫu nhiên (muốn đăng nhập bằng mật khẩu thì dùng "Quên mật khẩu").
- Khi chạy thật đặt `BACKEND_URL` (HTTPS công khai) và `FRONTEND_URL`; redirect URI phải khớp từng ký tự với cái đã khai báo. Cookie `refresh_token` đang đặt `Secure`, nên khác miền với frontend thì cần HTTPS cả hai.

### Cơ sở dữ liệu và Flyway
Schema do **Flyway** quản lý (`src/main/resources/db/migration`). Hibernate chỉ kiểm tra (`ddl-auto=validate`) rằng entity khớp với bảng, không còn tự sửa bảng.

- **DB trống:** chạy ứng dụng là Flyway tự tạo toàn bộ bảng từ `V1__baseline.sql`.
- **DB đã có dữ liệu** (đã chạy bản cũ): lần đầu Flyway ghi nhận nó là phiên bản 1 và bỏ qua `V1`, dữ liệu giữ nguyên. Nếu DB cũ hơn bản baseline (thiếu cột/bảng) thì Hibernate báo `Schema-validation: missing ...`; chạy một lần với biến môi trường `DDL_AUTO=update` để bổ sung, sau đó bỏ biến này.
- **Muốn đổi schema** (thêm cột, thêm bảng): thêm file mới `V2__mo_ta.sql` cạnh `V1`, không sửa file đã chạy. Entity và migration phải khớp nhau, nếu không ứng dụng không khởi động.
- Flyway 9.22 (do Spring Boot 3.2.4 quản lý) in cảnh báo "MySQL 8.4 is newer than this version of Flyway"; chỉ là cảnh báo, vẫn chạy bình thường.
- `V15` dọn dẹp: xóa hai vai trò thừa của dữ liệu mẫu cũ (SALER, Upper Management, chỉ khi chưa ai dùng), đặt ràng buộc **unique** cho `users.email` (migration báo lỗi nếu DB đã có hai tài khoản trùng email: hãy gộp hoặc đổi tên trước) và xóa refresh token cũ (mọi người đăng nhập lại một lần). Từ bản này cột `refresh_token` chỉ lưu SHA-256 của token, không còn lưu nguyên văn.
- `V18` thêm bảng `user_identities` (tài khoản Google / Facebook / LinkedIn nào thuộc người dùng nào; duy nhất theo nhà cung cấp + id bên đó).
- `V17` thêm `users.email_verified` (mặc định 1 cho tài khoản cũ) và bảng `email_verification_tokens`.
- `V16` thêm cột `orders.method` (MoMo/VNPay/VietQR/ZaloPay/Ngân hàng/Thẻ), cho phép NULL để giữ các đơn cũ.
- Log SQL mặc định tắt; đặt `SHOW_SQL=true` trong `.env` khi cần gỡ lỗi.
- `./gradlew test` chạy các unit test cho phần logic thuần (kiểm tra chữ ký tệp, giới hạn đăng nhập, bộ lọc chặn trường nhạy cảm, chữ ký VNPay, định dạng lương) cộng với `contextLoads` (cần MySQL).

## Về tác giả
Mọi thông tin về Tác giả Hỏi Dân IT, các bạn có thể tìm kiếm tại đây:

Website chính thức: https://hoidanit.vn/

Youtube “Hỏi Dân IT” : https://www.youtube.com/@hoidanit

Tiktok “Hỏi Dân IT” :  https://www.tiktok.com/@hoidanit

Fanpage “Hỏi Dân IT” : https://www.facebook.com/askITwithERIC/

Udemy Hỏi Dân IT: https://www.udemy.com/user/eric-7039/

