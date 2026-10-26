package de.elbracht.smp.agent;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
class LocalBackupServiceTest {
 @TempDir Path root;
 @Test void missingWorldAndMembershipDataNeverPassValidation() throws Exception {
  assertThatThrownBy(()->LocalBackupService.validateWorld(root)).isInstanceOf(java.io.IOException.class);
  Files.createDirectory(root.resolve("world"));Files.writeString(root.resolve("world/level.dat"),"test-world");
  assertThatThrownBy(()->LocalBackupService.validateWorld(root)).isInstanceOf(java.io.IOException.class);
  Files.writeString(root.resolve("whitelist.json"),"[]");Files.writeString(root.resolve("ops.json"),"[]");
  assertThatCode(()->LocalBackupService.validateWorld(root)).doesNotThrowAnyException();
 }
 @Test void productionModeCannotEnableLocalWorldBackup() {
  var properties=new BackupProperties(true,root,root,root,root);
  assertThatThrownBy(()->new LocalBackupService(properties,StatusServiceTest.properties(25565,"docker-host-read-only"),null,null,null,null,null)).isInstanceOf(IllegalStateException.class);
 }
}
