// get contract template

Client -> Spring Controller: Client gửi yêu cầu GET đến /contract-templates/{page}/{size} với page và size là các tham số phân trang.

Spring Controller -> ContractTemplateService: Controller gọi phương thức findAllTemplates của ContractTemplateService với đối tượng Pageable được tạo từ page và size.

ContractTemplateService -> ContractTemplateRepository: Service gọi phương thức findAllContractTemplate của ContractTemplateRepository với đối tượng Pageable để lấy tất cả các mẫu hợp đồng.

ContractTemplateRepository -> ContractTemplateService: Repository trả về một Page<Object[]> chứa các mẫu hợp đồng.

Trường hợp có các hợp đồng được tìm thấy:



ContractTemplateService -> ContractTemplateDto: Service xây dựng danh sách ContractTemplateDto từ mảng đối tượng Object[].

ContractTemplateDto -> ContractTemplateService: ContractTemplateDto trả về danh sách các DTO.

ContractTemplateService -> PageImpl: Service tạo đối tượng Page<ContractTemplateDto> từ danh sách ContractTemplateDto.

PageImpl -> ContractTemplateService: PageImpl trả về đối tượng Page<ContractTemplateDto>.

ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thành công chứa Page<ContractTemplateDto>.

Trường hợp không có hợp đồng nào được tìm thấy:



ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse trống.

Spring Controller -> Client: Controller gửi phản hồi đến client với đối tượng BaseResponse mô tả kết quả của yêu cầu.



// delete

Client -> Spring Controller: Client gửi yêu cầu DELETE đến /contracts/{id} với id là mã hợp đồng mẫu cần xóa.

Spring Controller -> ContractTemplateService: Controller gọi phương thức delete của ContractTemplateService với id của hợp đồng mẫu.

ContractTemplateService -> ContractTemplateRepository: Service gọi phương thức findById của ContractTemplateRepository để tìm hợp đồng mẫu theo id.

ContractTemplateRepository -> ContractTemplateService: Repository trả về một Optional<ContractTemplate> chứa hợp đồng mẫu nếu tìm thấy.

Trường hợp hợp đồng mẫu được tìm thấy:



ContractTemplateService -> ContractTemplate: Service thiết lập thuộc tính markDeleted của hợp đồng mẫu bằng true.

ContractTemplateService -> ContractTemplateRepository: Service gọi phương thức save của ContractTemplateRepository để lưu hợp đồng mẫu đã cập nhật.

ContractTemplateRepository -> ContractTemplateService: Repository trả về đối tượng hợp đồng mẫu đã được lưu.

ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thành công với thông báo "Delete Successfully".

Trường hợp hợp đồng mẫu không được tìm thấy:



ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thất bại với thông báo "the contract template not exist".

Spring Controller -> Client: Controller gửi phản hồi đến client với đối tượng BaseResponse mô tả kết quả của yêu cầu.





// create

Client -> Spring Controller: Client gửi yêu cầu POST đến /contracts với ContractTemplateRequest trong phần thân yêu cầu.

Spring Controller -> ContractTemplateService: Controller gọi phương thức createContract của ContractTemplateService với contractRequest.

ContractTemplateService -> ContractTemplate: Service tạo một đối tượng ContractTemplate mới.

ContractTemplateService: Thiết lập các thuộc tính của ContractTemplate từ contractRequest (như nameContract, numberContract, ruleContract, termContract, name, address, taxNumber, presenter, position, businessNumber, bankId, bankName, bankAccOwer, email, và createdDate).

ContractTemplateService -> ContractTemplateRepository: Service gọi phương thức save của ContractTemplateRepository để lưu đối tượng template.

Trường hợp lưu thành công:



ContractTemplateRepository -> ContractTemplateService: Repository trả về đối tượng template đã được lưu.

ContractTemplateService -> ContractTemplateDto: Service xây dựng một đối tượng ContractTemplateDto từ template đã được lưu.

ContractTemplateDto -> ContractTemplateService: DTO trả về đối tượng ContractTemplateDto.

ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thành công với thông báo "Create successfully".

Trường hợp lưu thất bại:



ContractTemplateRepository -> ContractTemplateService: Repository ném ra một ngoại lệ.

ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thất bại với thông báo lấy từ ngoại lệ.

Spring Controller -> Client: Controller gửi phản hồi đến client với đối tượng BaseResponse mô tả kết quả của yêu cầu.





// update

Client -> Spring Controller: Client gửi yêu cầu PUT đến /contracts/{id} với ContractTemplateRequest trong phần thân yêu cầu và id là mã hợp đồng mẫu cần cập nhật.

Spring Controller -> ContractTemplateService: Controller gọi phương thức update của ContractTemplateService với id và contractRequest.

ContractTemplateService -> ContractTemplateRepository: Service gọi phương thức findById của ContractTemplateRepository để tìm hợp đồng mẫu theo id.

ContractTemplateRepository -> ContractTemplateService: Repository trả về một Optional<ContractTemplate> chứa hợp đồng mẫu nếu tìm thấy.

Trường hợp hợp đồng mẫu được tìm thấy:



ContractTemplateService: Service cập nhật các thuộc tính của ContractTemplate từ contractRequest bằng cách sử dụng DataUtil.isNullObject để xử lý các giá trị null.

ContractTemplateService -> ContractTemplateRepository: Service gọi phương thức save của ContractTemplateRepository để lưu đối tượng template đã được cập nhật. Trường hợp lưu thành công:

ContractTemplateRepository -> ContractTemplateService: Repository trả về đối tượng template đã được lưu.

ContractTemplateService -> ContractTemplateDto: Service xây dựng một đối tượng ContractTemplateDto từ template đã được lưu.

ContractTemplateDto -> ContractTemplateService: DTO trả về đối tượng ContractTemplateDto.

ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thành công với thông báo "Update Successfully".

Trường hợp lưu thất bại:

ContractTemplateRepository -> ContractTemplateService: Repository ném ra một ngoại lệ.

ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thất bại với thông báo lấy từ ngoại lệ.

Trường hợp hợp đồng mẫu không được tìm thấy:



ContractTemplateService -> Spring Controller: Service trả về đối tượng BaseResponse thất bại với thông báo "the contract template not exist".

Spring Controller -> Client: Controller gửi phản hồi đến client với đối tượng BaseResponse mô tả kết quả của yêu cầu.











---------------------------------------------- old contract ------------------------------------------------------







// list old  contract

Client -> Spring Controller: Client gửi yêu cầu GET đến /contracts với các tham số page và size.

Spring Controller -> OldContractService: Controller gọi phương thức getContracts của OldContractService với các tham số page và size.

OldContractService -> OldContractRepository: Service gọi phương thức findAll của OldContractRepository, truyền vào đối tượng Pageable.

OldContractRepository -> Database: Repository thực thi câu lệnh SQL để lấy các hợp đồng từ cơ sở dữ liệu nơi is_deleted bằng 0, giới hạn kết quả bằng size và bỏ qua page * size.

OldContractRepository -> OldContractService: Repository trả về một đối tượng Page<OldContract> chứa các kết quả từ cơ sở dữ liệu.

OldContractService -> Spring Controller: Service chuyển đổi Page<OldContract> thành Page<OldContractDto> và trả về cho controller.

Spring Controller -> Client: Controller gửi phản hồi 200 OK đến client, với Page<OldContractDto> trong nội dung phản hồi.



// upload old contract

Client -> Spring Controller: Client gửi yêu cầu POST đến /contracts với Authorization token, đối tượng CreateUpdateOldContract, và mảng MultipartFile chứa các hình ảnh hợp đồng.

Spring Controller -> OldContractService: Controller gọi phương thức create của OldContractService với các tham số bearerToken, contractDto, và images.

OldContractService -> JwtService: Service gọi extractUsername của JwtService để lấy email từ token.

JwtService -> OldContractService: JwtService trả về email của người dùng.

OldContractService -> OldContract: Service thiết lập các thuộc tính cho đối tượng OldContract.



OldContractService -> CloudinaryService: Service gọi uploadImage của CloudinaryService để tải lên hình ảnh đầu tiên.

CloudinaryService -> OldContractService: CloudinaryService trả về URL của file.

OldContractService -> PdfUtils: Service gọi getTextFromPdfFile của PdfUtils để lấy nội dung từ file PDF.

PdfUtils -> OldContractService: PdfUtils trả về nội dung của file PDF.

OldContractService -> OldContractRepository: Service gọi save của OldContractRepository để lưu hợp đồng.

OldContractRepository -> OldContractService: Repository trả về đối tượng hợp đồng đã lưu.

OldContractService -> ContractTypeService: Service gọi getContractTypeById của ContractTypeService để lấy loại hợp đồng.

ContractTypeService -> OldContractService: ContractTypeService trả về loại hợp đồng.

OldContractService -> ElasticSearchService: Service gọi indexDocument của ElasticSearchService để lập chỉ mục hợp đồng.

ElasticSearchService -> OldContractService: ElasticSearchService xác nhận lập chỉ mục thành công.

OldContractService -> Spring Controller: Service trả về đối tượng BaseResponse thành công.



OldContractService -> PdfUtils: Service gọi process của PdfUtils để xử lý template với các hình ảnh.

PdfUtils -> OldContractService: PdfUtils trả về nội dung HTML.

OldContractService -> PdfUtils: Service gọi generatePdf của PdfUtils để tạo file PDF từ HTML.

PdfUtils -> OldContractService: PdfUtils trả về file PDF.

OldContractService -> OldContractController

Controller gửi phản hồi đến client với đối tượng BaseResponse mô tả kết quả của yêu cầu.



//delete old contract

Client -> Spring Controller: Client gửi yêu cầu DELETE đến /contracts/{id} với id là mã hợp đồng cần xóa.

Spring Controller -> OldContractService: Controller gọi phương thức delete của OldContractService với id của hợp đồng.

OldContractService -> OldContractRepository: Service gọi phương thức findById của OldContractRepository để tìm hợp đồng theo contractId.

OldContractRepository -> OldContractService: Repository trả về một Optional<OldContract> chứa hợp đồng nếu tìm thấy.

Trường hợp hợp đồng được tìm thấy:



OldContractService -> OldContract: Service thiết lập thuộc tính isDeleted của hợp đồng bằng true.

OldContractService -> OldContractRepository: Service gọi phương thức save của OldContractRepository để cập nhật hợp đồng.

OldContractRepository -> OldContractService: Repository trả về đối tượng hợp đồng đã cập nhật.

OldContractService -> ElasticSearchService: Service gọi phương thức deleteDocumentById của ElasticSearchService để xóa tài liệu khỏi ElasticSearch.

ElasticSearchService -> OldContractService: ElasticSearchService xác nhận việc xóa tài liệu thành công.

OldContractService -> Spring Controller: Service trả về đối tượng BaseResponse thành công.

Trường hợp hợp đồng không được tìm thấy:



OldContractService -> Spring Controller: Service trả về đối tượng BaseResponse thất bại.

Spring Controller -> Client: Controller gửi phản hồi đến client với đối tượng BaseResponse mô tả kết quả của yêu cầu. 
