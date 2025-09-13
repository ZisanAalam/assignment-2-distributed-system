import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        BasicRESTFunctionalityTest.class,
        ComprehensiveTest.class
})

public class AllTest {
}
