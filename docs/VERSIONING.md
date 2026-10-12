---
author: @y9san9
---

# Versioning

After careful consideration, I decided to switch the way versioning on backend
works, because it is the only way I can think of to make versioning consistent
for functions and types.

## Before

Before this change, versioning worked for funcitons only, it was expected that
in case of schema change, it is the responsibility of the routing layer to rule
out the compatibility changes.

```kotlin
object FooRouting {
    // /user/...
    fun foo() { /* ... */ } // **first** version of this method
    // /user/2/...
    fun foo2() { /* ... */ } // **second** version of this method
}
```

`foo` resolves to the first version of function, because old clients will
continue to call this endpoint. However, at some point there might be such
version changes that will lead to compatiblity required in the core of the
backend, not just in the presentation layer.

Then, types will have to have this suffix as well. If doing that consistently,
it will lead to suffixes in types in the following way:

```kotlin
data class Foo() // **first** version of this model
data class Foo2() // **second** version of this model
```

**However**, that approach is a hell from the practical standpoint of view.
Because *every feature is versioned separately*. Then, to develop backend, you
will have to remember *every version for every feature*. Which creates friction
that must be avoided.

So, the next logical step is to version models in the opposite way:

```kotlin
data class Foo1() // **first** version of this model
data class Foo() // **second** version of this model
```

This is much better for development, but creates an inconsistency in how
versioning works for *functions that represent routes* and all other
identifiers. **Which also includes core functions**.

And I cannot think of any other good way of versioning that will allow to keep
versioning for routing functions.

## After

Routing functions will require to accept `X-Api-Version` header that will decide
which version of the method should be called. That way consistency is achieved:

```kotlin
object FooRouting {
    fun foo(request: Request) {
        when (request.version) {
            1 -> foo1()
            2 -> foo2()
        }
    }

    fun foo1() { /* ... */ } // **first** version of this method
    fun foo2() { /* ... */ } // **second** version of this method
}

data class Foo1() // **first** version of this model
data class Foo2() // **second** version of this model

typealias Foo = Foo2 // typealias is optional, otherwise no suffix = latest
```

Then, for the convenience of clients implementation, version should be unified
across of all function:

```kotlin
object FooRouting {
    fun foo(request: Request) {
        when {
            request.version >= 5 -> foo5()
            request.version >= 1 -> foo1() // 1..4 `foo` function did not change
        }
    }

    fun foo1() { /* ... */ } // **first** version of this method
    fun foo5() { /* ... */ } // **fifth** version of this method
}

data class Foo1() // **first** version of this model
data class Foo5() // **fifth** version of this model

typealias Foo = Foo5 // typealias is optional, otherwise no suffix = latest
```

Now clients can stop caring about versions of each individual function:

```kotlin
class FriendlyClient {
    companion object {
        const val ApiVersion = 2
    }
}
```

This also makes application code more readable since it removes old function
from `Main.kt`.

## Migration Guide

All functions that now have suffix `2` should be kept (no 3rd versions yet).
When the first breaking change occurs, `X-Api-Version` is set to `2`, absense of
`X-Api-Version` means `1`.

Functions that not yet had breaking changes, **including new functions** should
not have any routing logic. It is only when the first breaking change of that
function occurs, when you should add version routing logic.

Same for types. They do not contain version when they were introduced. Only once
breaking change occur, they are frozen with the api version before breaking
change.
