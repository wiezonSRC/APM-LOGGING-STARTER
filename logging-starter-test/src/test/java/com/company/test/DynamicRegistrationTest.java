package com.company.test;

import com.company.test.batch.DynamicJobRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.Job;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
class DynamicRegistrationTest {

    @Autowired private DynamicJobRunner dynamicJobRunner;
    @Autowired private Job noListenerJob;

    @Test
    @DisplayName("AbstractInitJob 스타일의 리스너 동적 주입 테스트")
    void testDynamicRegistration(CapturedOutput output) throws Exception {
        // 리스너가 없는 Job을 Runner를 통해 실행 (Runner 내부에서 동적 주입)
        dynamicJobRunner.run(noListenerJob);

        String out = output.getOut();

        // 결과 검증: 리스너가 주입되어 로그가 남았는지 확인
        assertThat(out).contains("[BATCH_PROD]");
        assertThat(out).contains("job_name=noListenerJob");
        assertThat(out).contains("step_name=noListenerStep");
        assertThat(out).contains("status=COMPLETED");

        System.out.println("Dynamic registration test passed!");
    }
}
