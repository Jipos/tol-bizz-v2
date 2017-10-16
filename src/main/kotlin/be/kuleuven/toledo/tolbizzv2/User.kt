package be.kuleuven.toledo.tolbizzv2

import org.springframework.data.repository.CrudRepository
import javax.persistence.*
import javax.validation.constraints.NotNull

// We need this interface because kotlin delegation doesn't work on concrete classes
interface UserDraft {
    val firstName: String
    val lastName: String
    val email: String
}

data class Foo(val foo: String, val bar: String, val baz: Long)

class UserDraftImpl(
        override val firstName: String,
        override val lastName: String,
        override val email: String): UserDraft

class User(
        val id: String,
        userDraft: UserDraft): UserDraft by userDraft

@MappedSuperclass
open class UserDraft2(
        @NotNull @Column val firstName: String,
        @NotNull @Column val lastName: String,
        @NotNull @Column val email: String)

@Entity
@Table(name = "User")
class User2(@Id val id: String,
            firstName: String,
            lastName: String,
            email: String): UserDraft2(firstName, lastName, email) {
    constructor(id: String, userDraft: UserDraft2): this(id, userDraft.firstName, userDraft.lastName, userDraft.email)
}

interface UserRepository: CrudRepository<User2, String>

fun main(args: Array<String>) {

    val draft = UserDraftImpl("kevin", "rogiers", "foo@bar")
    val user = User("id", draft)

    println("[${user.id}] ${user.firstName} ${user.lastName} - ${user.email}")

    val draft2 = UserDraft2("kevin", "rogiers", "foo@bar")
    val user2 = User2("id2", draft2)

    println("[${user2.id}] ${user2.firstName} ${user2.lastName} - ${user2.email}")

    val foo = Foo("one", "two", 1L)
    val bar = foo.copy(bar = "override?")


}