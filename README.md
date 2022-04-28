# Behavior of `@EnableMethodSecurity` vs `@EnableGlobalMethodSecurity`


## Problem


When `@PreAuthorize` is specified on each method, the newer `@EnableMethodSecurity` works as described.

Interface-level `@PreAuthorize` attributes don't work with `@EnableMethodSecurity`, but they do work with the no-longer-recommended `@EnableGlobalMethodSecurity`. This is a change to previous behavior, conflicts with the Spring Data REST docs, and could cause developers to accidentally expose their data (repositories previously secured at the interface level) when upgrading to the latest Spring Security concepts.

## Example

This repo contains a sample application that shows the issue.

The app has one Spring Data REST repository, which is secured at the interface level, with one method specifically secured.

Expected behavior: `@EnableMethodSecurity` and `@EnableGlobalMethodSecurity` have the same behavior in accordance w/ the docs
Actual behavior: With `@EnableMethodSecurity`, `@PreAuthorize` works on methods, but not for interfaces

### Demo

Run w/ `@EnableGlobalMethodSecurity` (old) to see it work

```shell
mvn spring-boot:run

# expect 403 Forbidden at the repository (interface) level
curl http://user:password@localhost:8080/things

# expect 403 Forbidden at the method level
curl http://user:password@localhost:8080/things/1
```

Now, update `MethodSecurityConfig.java` to use `@EnableMethodSecurity`. Ex:

```java
@EnableMethodSecurity
//@EnableGlobalMethodSecurity(prePostEnabled = true)
@Configuration
public class MethodSecurityConfig {

}

```

Run the demo again, and observe the results

```shell

mvn spring-boot:run

# expect 403 Forbidden at the repository (interface) level, but..
# ERROR: I should not be allowed to access this data!
curl http://user:password@localhost:8080/things

# expect 403 Forbidden at the method level
curl http://user:password@localhost:8080/things/1
```

This is unexpected - I am able to access `/things`, and my API is now leaking data that I did not intend to expose.


## Spring Data REST

[Spring Data REST docs](https://docs.spring.io/spring-data/rest/docs/current/reference/html/#security) say to use a `@PreAuthorize()` annotation at the interface level to secure an entire repository

Snippet from docs:

```java
@PreAuthorize("hasRole('ROLE_USER')") // 1
public interface PreAuthorizedOrderRepository extends CrudRepository<Order, UUID> {

	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Override
	Optional<Order> findById(UUID id);

	@PreAuthorize("hasRole('ROLE_ADMIN')") // 2
	@Override
	void deleteById(UUID aLong);

	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Override
	void delete(Order order);

	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Override
	void deleteAll(Iterable<? extends Order> orders);

	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@Override
	void deleteAll();
}
```

```
1. This Spring Security annotation secures the entire repository. The Spring Security SpEL expression indicates that the principal must have ROLE_USER in its collection of roles.
2. To change method-level settings, you must override the method signature and apply a Spring Security annotation. In this case, the method overrides the repository-level settings with the requirement that the user have ROLE_ADMIN to perform a delete.
```

## Spring Security

Spring Security says to use `@EnableMethodSecurity` starting in 5.6.

Snip:

```java
@EnableMethodSecurity
public class MethodSecurityConfig {
	// ...
}
```

There seems to be a difference in behavior in how the `@PreAuthorize` attribute is resolved

* (new) [PreAuthorizeAuthorizationManager.java](https://github.com/spring-projects/spring-security/blob/48ac100a92ac060a92ac49d7a505bf9ec3643404/core/src/main/java/org/springframework/security/authorization/method/PreAuthorizeAuthorizationManager.java#L88) L88 - the `preAuthorize` attribute returns null when it is defined on the interface
* (old) [PrePostAnnotationSecurityMetadataSource](https://github.com/spring-projects/spring-security/blob/48ac100a92ac060a92ac49d7a505bf9ec3643404/core/src/main/java/org/springframework/security/access/prepost/PrePostAnnotationSecurityMetadataSource.java#L105) `findAttribute()` - this lookup is more involved, and successfully returns the `@PreAuthorize` attribute for evaluation when it is declared on an interface vs on each class
