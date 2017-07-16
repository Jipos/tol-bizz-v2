package be.kuleuven.toledo.tolbizzv2

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController


@SpringBootApplication
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

class UsernameOnlyUserDetailsService : UserDetailsService {
    override fun loadUserByUsername(username: String?) =
            User("$username", "", listOf())

}

fun main(args: Array<String>) {
    SpringApplication.run(TolBizzV2Application::class.java, *args)
}

@RestController
class MyController @Autowired constructor(val service: MyService) {

    @GetMapping("/user/{id}")
    fun getUser(@PathVariable id: String, @AuthenticationPrincipal actor: User) =
            service.findById(id, actor)

}

@Service
class MyService {

    fun findById(id: String,  actor: User): Map<String, String> {
        println("id=$id; actor=${actor.name}")
        return mapOf("id" to id)
    }

}