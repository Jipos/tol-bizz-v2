package be.kuleuven.toledo.tolbizzv2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.MethodParameter
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.multipart.MultipartFile
import java.io.Serializable
import java.lang.annotation.Inherited
import java.util.*
import java.util.function.Predicate
import javax.servlet.http.HttpServletRequest
import kotlin.reflect.KClass

@SpringBootApplication
@EnableGlobalMethodSecurity(prePostEnabled = true)
class TolBizzV2Application {

    @Bean
    fun kulmoreunifieduidPreauthFilter(): RequestHeaderAuthenticationFilter {
        val userDetailsService: AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> =
                UserDetailsByNameServiceWrapper(UsernameOnlyUserDetailsService())
        val authenticationProvider = PreAuthenticatedAuthenticationProvider()
        authenticationProvider.setPreAuthenticatedUserDetailsService(userDetailsService)
        val authenticationManager = ProviderManager(listOf(authenticationProvider))
        val filter = RequestHeaderAuthenticationFilter()
        filter.setAuthenticationManager(authenticationManager)
        filter.setPrincipalRequestHeader("kulmoreunifieduid")
        return filter
    }

}

fun main(args: Array<String>) {
    SpringApplication.run(TolBizzV2Application::class.java, *args)
}

class UsernameOnlyUserDetailsService : UserDetailsService {

    override fun loadUserByUsername(username: String?) =
            User("$username", "", listOf(MyGrantedAuthority("USER")))

}

class MyGrantedAuthority(@JvmField val authority: String): GrantedAuthority {
    override fun getAuthority() = authority
    override fun toString() = authority
}

@RestController
class MyController @Autowired constructor(val service: MyService) {

    @GetMapping("/user/{id}")
    fun getUser(@PathVariable id: String) = service.findById(id)

    @GetMapping("/authorities")
    fun getAuthorities(@AuthenticationPrincipal principal: User): List<String> {
        println("actor=${principal.authorities}")
        return principal.authorities.map { it.authority }
    }

    @GetMapping("/create")
    fun create(): Link = service.create(NewLink("foobar"))

    @GetMapping("/update")
    fun update(): Link = service.update(Link("id", "name"))

    @GetMapping("/delete")
    fun delete(): Link = service.delete(Link("id", "name"))

    @GetMapping("/get")
    fun get(): Link = service.get("id")

    @GetMapping("/createUser")
    fun createRepoUser(): User2 = service.createUser(UserDraft2("first", "last", "email"))

    @GetMapping("/getUser/{id}")
    fun getRepoUser(@PathVariable id: String): Optional<User2> = service.getUser(id)
}

fun principal(): Optional<Authentication> = Optional.ofNullable(SecurityContextHolder.getContext().authentication)


@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@Retention
@Inherited
@MustBeDocumented
@PreAuthorize("hasPermission(#link, 'create')")
annotation class CanCreateLink

// http://blog.novoj.net/2012/03/27/combining-custom-annotations-for-securing-methods-with-spring-security/
interface MyService {

    fun findById(id: String): Map<String, String>

    //@PreAuthorize("hasPermission(#link, 'create')")
    @CanCreateLink
    fun create(link: NewLink): Link

    @PreAuthorize("hasPermission(#link, 'update')")
    fun update(link: Link): Link

    @PreAuthorize("hasPermission(#link, 'delete')")
    fun delete(link: Link): Link

    @PostAuthorize("hasPermission(#returnObject, 'read')")
    fun get(id: String): Link

    fun getUser(userId: String): Optional<User2>
    fun createUser(user: UserDraft2): User2
}

@Service
class MyServiceImpl(val userRepo: UserRepository): MyService {

    override fun findById(id: String): Map<String, String> {
        val principal = principal()
        println("id=$id; actor=${principal.map { "${it.name} -> ${it.authorities}" }.orElse("unknown")}")
        return mapOf("id" to id)
    }

    override fun create(link: NewLink): Link = Link("TODO", link.name)

    override fun update(link: Link): Link = link

    override fun delete(link: Link): Link = link

    override fun get(id: String): Link = Link(id, "TODO")

    override fun createUser(user: UserDraft2): User2 = userRepo.save(User2("newId", user))

    override fun getUser(userId: String): Optional<User2> = userRepo.findById(userId)

}

open class NewLink(val name: String)
class Link(val id: String, name: String): NewLink(name)

@Component
class MyPermissionEvaluator: PermissionEvaluator {

    override fun hasPermission(authentication: Authentication?, targetId: Serializable?, targetType: String?, permission: Any?): Boolean {
        throw UnsupportedOperationException("Only targetDomainObject based permission checks are supported.")
    }

    override fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, permission: Any?): Boolean {
        println("hasPermission? $authentication - $targetDomainObject - $permission")
        return true
    }
}

class ApplicationVersionMethodArgumentResolver(): HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) =
        parameter.parameterName == "version"

    override fun resolveArgument(parameter: MethodParameter, mavContainer: ModelAndViewContainer,
                                 webRequest: NativeWebRequest, binderFactory: WebDataBinderFactory): Any? {
        val servletRequest: HttpServletRequest = webRequest.getNativeRequest(HttpServletRequest::class.java)


        return webRequest.getHeader("X-Application-Version");
    }
}
interface PollablePermissionEvaluator: PermissionEvaluator, Predicate<Any>

class LinkPermissionEvaluator: PollablePermissionEvaluator {

    companion object {
        val clazz: KClass<Link> = Link::class
    }

    override fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, permission: Any?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasPermission(authentication: Authentication?, targetId: Serializable?, targetType: String?, permission: Any?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun test(obj: Any): Boolean = clazz.isInstance(obj)

}

class CompositePermissionEvaluator @Autowired constructor(val evaluators: List<PollablePermissionEvaluator>): PermissionEvaluator {

    override fun hasPermission(authentication: Authentication?, targetId: Serializable?, targetType: String?, permission: Any?): Boolean {
        throw UnsupportedOperationException("Only targetDomainObject based permission checks are supported.")
    }

    override fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, permission: Any?) =
        if (targetDomainObject == null) {
            false
        } else {
            evaluators.any { evaluator ->
                if (evaluator.test(targetDomainObject)) {
                    evaluator.hasPermission(authentication, targetDomainObject, permission);
                } else {
                    false
                }
            }
        }

}