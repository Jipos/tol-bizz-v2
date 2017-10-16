package be.kuleuven.toledo.tolbizzv2

import com.fasterxml.jackson.databind.type.TypeFactory
import org.apache.commons.logging.LogFactory
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.multipart.MultipartException
import javax.servlet.http.HttpServletRequest
import org.springframework.core.ResolvableType
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest
import org.springframework.web.multipart.MultipartHttpServletRequest
import org.springframework.web.util.WebUtils
import org.springframework.context.annotation.Configuration
import org.springframework.core.GenericTypeResolver
import org.springframework.core.io.Resource
import org.springframework.http.*
import org.springframework.http.converter.GenericHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.HttpMessageNotWritableException
import org.springframework.util.Assert
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*

fun <T> Optional<T>.isAbsent() = !this.isPresent

@Configuration
class MyWebMvcConfig : WebMvcConfigurer {

    override fun addArgumentResolvers(argumentResolvers: MutableList<HandlerMethodArgumentResolver>) {
        argumentResolvers.add(ExcelMethodArgumentResolver())
    }

    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        converters.add(ExcelToDtoHttpMessageConverter())
    }
}


@RestController
class ExcelDtoController() {

    // using custom argument resolver
    @PostMapping("/excelDto")
    fun excelDto(@Excel dtoList: List<BatchEnrollDto>): String {
        println(dtoList)

        return "finished"
    }

    // using custom message converter
    @PostMapping("/excelDto2")
    fun excelDto2(@RequestPart dtoList: List<BatchEnrollDto>): String {
        println(dtoList)

        return "finished"
    }

    // using default Spring stuff
    @PostMapping("/excelDto3")
    fun excelDto2(excel: MultipartFile): String {
        println(excel)

        return "finished"
    }

    @PostMapping("excelPart")
    fun excelPart(dtoList: MultipartFile): String {
        println("---- excel part")
        println(dtoList)
        return "finished"
    }



}

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Excel(
        /**
         * Whether or not the columns in the Excel must exactly math the fields of the
         * DTO, or whether the Excel may contain additional/unused columns.
         */
        val allowUnusedColumns: Boolean = false,
        /**
         * Whether or not an multipart Excel file is required
         */
        val required: Boolean = true,
        /**
         * The name of the multipart Excel file
         */
        val name: String = "")

/**
 * Adaption of Spring's RequestPartMethodArgumentResolver class.
 *
 * @See org.springframework.web.servlet.mvc.method.annotation.RequestPartMethodArgumentResolver
 */
//class ExcelMethodArgumentResolver(
//        messageConverters: List<HttpMessageConverter<*>>,
//        requestResponseBodyAdvice: List<Any>? = null):
//        AbstractMessageConverterMethodArgumentResolver(messageConverters, requestResponseBodyAdvice) {
class ExcelMethodArgumentResolver: HandlerMethodArgumentResolver {

    /**
     * Requires the following:
     *
     *  * annotated with `@Excel`
     *  * of type List
     *  * with a generic type
     *
     * can be wrapped in Optional
     */
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(Excel::class.java) &&
                parameter.parameterType == List::class.java &&
                getListParameterType(parameter) != null
    }

    private fun getListParameterType(methodParam: MethodParameter): Class<*>? {
        val paramType = methodParam.nestedParameterType
        if (List::class.java.isAssignableFrom(paramType)) {
            val valueType = ResolvableType.forMethodParameter(methodParam).asCollection().resolveGeneric()
            if (valueType != null) {
                return valueType
            }
        }
        return null
    }

    @Throws(Exception::class)
    override fun resolveArgument(parameter: MethodParameter, mavContainer: ModelAndViewContainer?,
                                 request: NativeWebRequest, binderFactory: WebDataBinderFactory?): Any? {
        println("#### RESOLVING EXCEL PARAM")

        val servletRequest = request.getNativeRequest(HttpServletRequest::class.java)
        if (servletRequest == null) {
            throw IllegalStateException("No HttpServletRequest")
        } else {
            return resolveArgument(parameter, servletRequest)
        }
    }

    @Throws(Exception::class)
    private fun resolveArgument(
            parameter: MethodParameter,
            servletRequest: HttpServletRequest/*,
            binderFactory: WebDataBinderFactory?*/): Any? {

        val excel = parameter.getParameterAnnotation(Excel::class.java)
        val isRequired = excel!!.required && !parameter.isOptional

        val name = getPartName(parameter, excel)

        val nonOptionalParameter = parameter.nestedIfOptional()

        val multipartFile: Optional<MultipartFile> =
                asMultipartHttpServletRequest(servletRequest)
                        .map { it.getFile(name) }

        val arg = multipartFile.map {
            // TODO: KR convert Excel multipart to DTO list
            println(it)
            listOf(BatchEnrollDto().apply {
                userId = "userId"
                learningUnitId = 1.0
                role = Calendar.getInstance().time
            })
        }

        if (multipartFile.isAbsent() && isRequired) {
            if (!isMultipartRequest(servletRequest)) {
                throw MultipartException("Current request is not a multipart request")
            } else {
                throw MissingServletRequestPartException(name)
            }
        }
        return arg.map { adaptArgumentIfNecessary(it, nonOptionalParameter) }
                .orElseThrow { MultipartException("Multipart file cannot be converted to DTO list") }

//        if (mpArg !== MultipartResolutionDelegate.UNRESOLVABLE) {
//            // TODO: KR convert Excel multipart to DTO list
//            arg = mpArg
//            println(arg)
//            arg = listOf(BatchEnrollDto().apply {
//                userId = "userId"
//                learningUnitId = 1.0
//                role = Calendar.getInstance().time
//            })
//        } else {
//            try {
//                val inputMessage = RequestPartServletServerHttpRequest(servletRequest!!, name)
//                arg = readWithMessageConverters<Any?>(inputMessage, parameter, parameter.nestedGenericParameterType)
//                if (binderFactory != null) {
//                    val binder = binderFactory.createBinder(request, arg, name)
//                    if (arg != null) {
//                        validateIfApplicable(binder, parameter)
//                        if (binder.bindingResult.hasErrors() && isBindExceptionRequired(binder, parameter)) {
//                            throw MethodArgumentNotValidException(parameter, binder.bindingResult)
//                        }
//                    }
//                    mavContainer?.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.bindingResult)
//                }
//            } catch (ex: MissingServletRequestPartException) {
//                if (isRequired) {
//                    throw ex
//                }
//            } catch (ex: MultipartException) {
//                if (isRequired) {
//                    throw ex
//                }
//            }
//        }
    }

    private fun getPartName(methodParam: MethodParameter, excel: Excel?): String {
        var partName: String? = excel?.name ?: ""
        if (partName!!.isEmpty()) {
            partName = methodParam.parameterName
            if (partName == null) {
                throw IllegalArgumentException("Request part name for argument type [" +
                        methodParam.nestedParameterType.name +
                        "] not specified, and parameter name information not found in class file either.")
            }
        }
        return partName
    }

    private fun asMultipartHttpServletRequest(request: HttpServletRequest) =
        if (isMultipartRequest(request))
            Optional.of(WebUtils.getNativeRequest(request, MultipartHttpServletRequest::class.java)
                ?:StandardMultipartHttpServletRequest(request))
        else
            Optional.empty()

    private fun isMultipartRequest(request: HttpServletRequest) =
        WebUtils.getNativeRequest(request, MultipartHttpServletRequest::class.java) != null
                || isMultipartContent(request)

    private fun isMultipartContent(request: HttpServletRequest): Boolean {
        val contentType = request.contentType
        return contentType != null && contentType.toLowerCase().startsWith("multipart/")
    }

    /**
     * Adapt the given argument against the method parameter, if necessary.
     * @param arg the resolved argument
     * @param parameter the method parameter descriptor
     * @return the adapted argument, or the original resolved argument as-is
     * @since 4.3.5
     */
    private fun adaptArgumentIfNecessary(arg: Any, parameter: MethodParameter) =
            when (parameter.parameterType) {
                is Optional<*> -> Optional.of(arg)
                else -> arg
            }
//        if (parameter.parameterType == Optional::class.java) {
//            Optional.ofNullable(arg)
//        } else arg

}


/**
 * Adaption of Spring's MultipartResolutionDelegate class.
 *
 * @See org.springframework.web.multipart.support.MultipartResolutionDelegate
 */
object ExcelResolutionDelegate {

    val UNRESOLVABLE = Any()


    fun isMultipartRequest(request: HttpServletRequest): Boolean {
        return WebUtils.getNativeRequest(request, MultipartHttpServletRequest::class.java) != null || isMultipartContent(request)
    }

    private fun isMultipartContent(request: HttpServletRequest): Boolean {
        val contentType = request.contentType
        return contentType != null && contentType.toLowerCase().startsWith("multipart/")
    }

    @Throws(Exception::class)
    fun resolveExcelArgument(name: String, parameter: MethodParameter, request: HttpServletRequest): Any? {

        var multipartRequest = WebUtils.getNativeRequest(request, MultipartHttpServletRequest::class.java)
        val isMultipart = multipartRequest != null || isMultipartContent(request)

        return if (multipartRequest != null && isMultipart) {
            multipartRequest.getFile(name)
        } else {
            UNRESOLVABLE
        }

//        return if (multipartRequest == null && isMultipart) {
//            StandardMultipartHttpServletRequest(request).getFile(name)
//        } else if (isMultipart){
//            request.getPart(name)
//        } else {
//            UNRESOLVABLE
//        }
    }

}

class ExcelToDtoHttpMessageConverter : GenericHttpMessageConverter<List<BatchEnrollDto>> {

    private val supportedMediaTypes = listOf(
            MediaType.valueOf("application/vnd.ms-excel"),
            MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    )

    override fun getSupportedMediaTypes(): List<MediaType> =
            Collections.unmodifiableList(this.supportedMediaTypes)

    override fun canRead(clazz: Class<*>, mediaType: MediaType?) = false

    @Throws(IOException::class)
    override fun read(clazz: Class<out List<BatchEnrollDto>>, inputMessage: HttpInputMessage) =
            throw UnsupportedOperationException("read is not supported for non-generic types")

    /**
     * Write is not supported
     */
    override fun canWrite(clazz: Class<*>, mediaType: MediaType?) = false

    /**
     * write not supported
     */
    @Throws(IOException::class, HttpMessageNotWritableException::class)
    override fun write(t: List<BatchEnrollDto>, contentType: MediaType?, outputMessage: HttpOutputMessage) =
            throw UnsupportedOperationException("write is not supported")

    /**
     * Write is not supported
     */
    override fun canWrite(type: Type?, clazz: Class<*>?, mediaType: MediaType?) = false

    /**
     * Write is not supported
     */
    override fun write(t: List<BatchEnrollDto>?, type: Type?, contentType: MediaType?, outputMessage: HttpOutputMessage?) =
            throw UnsupportedOperationException("write is not supported")

    override fun canRead(type: Type?, contextClass: Class<*>?, mediaType: MediaType?) =
            type != null && type is ParameterizedType
                && type.rawType is Class<*> && List::class.java.isAssignableFrom(type.rawType as Class<*>)
                && hasGenericTypeArgument(type)
                && canRead(mediaType)

    private fun hasGenericTypeArgument(type: ParameterizedType) =
            type.actualTypeArguments.size == 1 && type.actualTypeArguments[0] is Class<*>

    private fun getGenericTypeArgument(type: Type?) =
            (type as ParameterizedType).actualTypeArguments[0] as Class<*>

    private fun canRead(mediaType: MediaType?)  =
            getSupportedMediaTypes().any { it.includes(mediaType) }

    override fun read(type: Type?, contextClass: Class<*>?, inputMessage: HttpInputMessage): List<BatchEnrollDto> {
        val dtoType = getGenericTypeArgument(type)
        val workbook = openExcelFile(inputMessage)
        // TODO: KR convert inputMessage to dto list
        // dummy implementation
        return listOf(BatchEnrollDto().apply {
            userId = "userId"
            learningUnitId = 1.0
            role = Calendar.getInstance().time
        })
    }

    @Throws(Exception::class)
    private fun openExcelFile(inputMessage: HttpInputMessage):Workbook {
        val workbookStream = inputMessage.body
        if (!workbookStream.markSupported() && workbookStream !is PushbackInputStream) {
            throw IllegalStateException("InputStream MUST either support mark/reset, or be wrapped as a PushbackInputStream")
        }
        val workbook = WorkbookFactory.create(workbookStream)
        workbook.missingCellPolicy = Row.CREATE_NULL_AS_BLANK
        return workbook
    }

}