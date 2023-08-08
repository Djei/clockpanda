package djei.clockpanda.authnz

import djei.clockpanda.testing.ClockPandaSpringBootTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@ClockPandaSpringBootTest
class UserControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `test get user - no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user")
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    @WithMockUser(username = "test-user")
    fun `test get user - with authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user")
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(200)
    }
}
