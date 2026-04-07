package tree

import kotlinx.serialization.Serializable

/**
 * 符合 Postman Collection v2.1 规范的 Auth 设计。
 * 支持在 Collection、Folder、Request 级别定义。
 */
@Serializable
data class PostmanAuth(
    val type: String, // apikey, basic, bearer, digest, oauth1, oauth2, hawk, ntlm, awssig, edgegrid
    val apikey: List<AuthProperty>? = null,
    val basic: List<AuthProperty>? = null,
    val bearer: List<AuthProperty>? = null,
    // 其他类型可按需扩展
)

@Serializable
data class AuthProperty(
    val key: String,
    val value: String,
    val type: String = "string"
)

/**
 * 助手函数：从属性列表中获取特定键的值
 */
fun List<AuthProperty>?.findValue(key: String): String? = this?.find { it.key == key }?.value
