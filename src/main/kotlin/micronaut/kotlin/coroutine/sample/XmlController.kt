package micronaut.kotlin.coroutine.sample

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import org.slf4j.LoggerFactory

@Controller("/xml")
class XmlController(
    val xmlMapper: XmlMapper
) {
    /**
     * ロガー
     */
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * XML レスポンス
     *
     * curl -i http://localhost:8080/xml
     */
    @Get("/")
    @Produces(CustomMediaType.APPLICATION_XML_UTF8)
    fun index(): XmlData {
        return XmlData(10, "ふー", "ばー", listOf("リスト1", "リスト2", "リスト3"))
    }

    /**
     * XMLMapper を直接利用する
     *
     * curl -i http://localhost:8080/xml/mapper
     * @return
     */
    @Get("/mapper")
    @Produces(CustomMediaType.APPLICATION_XML_UTF8)
    fun mapper(): String {
        return xmlMapper.writeValueAsString(
            XmlData(10, "ふー", "ばー", listOf("リスト1", "リスト2", "リスト3")))
    }

    /**
     * カスタム変換
     *
     * curl -i http://localhost:8080/xml/custom
     * @return
     */
    @Get("/custom")
    @Produces(CustomMediaType.APPLICATION_XML_UTF8)
    fun custom(): CustomXmlData {
        return CustomXmlData(
            10,
            "ふー",
            "ばー",
            listOf("リスト1", "リスト2", "リスト3"),
            mapOf("map1" to "item1", "map2" to "item2")
        )
    }
}

data class XmlData(val id: Int, val name: String, val memo: String, val tasks: List<String>)

@JacksonXmlRootElement(localName = "CustomRootName")
data class CustomXmlData(
    @JacksonXmlProperty(localName = "CustomID")
    val id: Int,
    val name: String,
    @JacksonXmlCData(true)
    val memo: String,
    @JacksonXmlElementWrapper(localName = "CustomList", useWrapping = true)
    @JacksonXmlProperty(localName = "CustomListItem", isAttribute = false)
    val tasks: List<String>,
    @JacksonXmlProperty(localName = "CustomMap")
    val maps: Map<String, String>
)
