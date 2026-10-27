import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** Prints a password hash in the backend's format ({bcrypt}...), for seeding the E2E Admin. */
public class Hash {
    public static void main(String[] args) {
        System.out.println(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(args[0]));
    }
}
