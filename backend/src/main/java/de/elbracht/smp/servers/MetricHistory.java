package de.elbracht.smp.servers;
import com.fasterxml.jackson.databind.JsonNode;
import de.elbracht.smp.identity.ServerAccess;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Stores measured container samples only; unavailable periods are gaps, never zero values. */
@Service
public class MetricHistory {
 public record Sample(Instant observedAt,double cpuPercent,long memoryUsed,long memoryLimit,long networkReceived,long networkSent,long diskRead,long diskWritten,int pids){}
 public record CollectionStatus(Instant checkedAt,String code){}
 public record History(boolean configured,int intervalSeconds,int retentionDays,List<Sample> samples,CollectionStatus collectionStatus){}
 private final Map<String,CollectionStatus> collection=new java.util.concurrent.ConcurrentHashMap<>();
 private final JdbcTemplate jdbc;private final HostAgent agent;private final boolean enabled;
 public MetricHistory(JdbcTemplate jdbc,HostAgent agent,@Value("${portal.metrics-history-enabled:false}")boolean enabled){this.jdbc=jdbc;this.agent=agent;this.enabled=enabled;}
 @Scheduled(fixedDelay=30000,initialDelay=30000,scheduler="metricsScheduler")
 public void collect(){
  if(!enabled)return;
  for(String server:jdbc.query("SELECT id FROM managed_server ORDER BY id LIMIT 100",(rs,n)->rs.getString(1)))try{
   var observed=agent.diagnostics(server,"metrics");if(observed==null||!server.equals(observed.serverId())||observed.reasonCode()!=null){collection.put(server,new CollectionStatus(Instant.now(),"SOURCE_UNAVAILABLE"));continue;}var sample=parse(observed,Instant.now());if(sample==null){collection.put(server,new CollectionStatus(Instant.now(),"INVALID_MEASUREMENT"));continue;}
   try{jdbc.update("INSERT INTO metric_sample VALUES (?,?,?,?,?,?,?,?,?,?)",server,OffsetDateTime.ofInstant(sample.observedAt(),ZoneOffset.UTC),sample.cpuPercent(),sample.memoryUsed(),sample.memoryLimit(),sample.networkReceived(),sample.networkSent(),sample.diskRead(),sample.diskWritten(),sample.pids());collection.put(server,new CollectionStatus(Instant.now(),"STORED"));}catch(org.springframework.dao.DataAccessException error){collection.put(server,new CollectionStatus(Instant.now(),"STORAGE_UNAVAILABLE"));}
  }catch(Exception ignored){collection.put(server,new CollectionStatus(Instant.now(),"SOURCE_UNAVAILABLE"));}
  jdbc.update("DELETE FROM metric_sample WHERE observed_at<?",OffsetDateTime.now(ZoneOffset.UTC).minusDays(7));
 }
 public History read(String server,int hours){
  var samples=new ArrayList<>(jdbc.query("SELECT * FROM metric_sample WHERE server_id=? AND observed_at>=? ORDER BY observed_at DESC LIMIT 3000",(rs,n)->new Sample(rs.getObject("observed_at",OffsetDateTime.class).toInstant(),rs.getDouble("cpu_percent"),rs.getLong("memory_used"),rs.getLong("memory_limit"),rs.getLong("network_received"),rs.getLong("network_sent"),rs.getLong("disk_read"),rs.getLong("disk_written"),rs.getInt("pids")),server,OffsetDateTime.now(ZoneOffset.UTC).minusHours(hours)));Collections.reverse(samples);return new History(enabled,30,7,List.copyOf(samples),collection.getOrDefault(server,new CollectionStatus(null,enabled?"WAITING":"DISABLED")));
 }
 static Sample parse(HostAgent.Diagnostics value,Instant now){
  if(value==null||value.reasonCode()!=null||value.data()==null||value.observedAt()==null||value.observedAt().isBefore(now.minusSeconds(60))||value.observedAt().isAfter(now.plusSeconds(10)))return null;
  try{JsonNode data=value.data();String cpu=field(data,"CPUPerc");if(!cpu.matches("[0-9]+(?:\\.[0-9]+)?%"))return null;double percent=Double.parseDouble(cpu.substring(0,cpu.length()-1));if(!Double.isFinite(percent)||percent<0||percent>1000000)return null;
   long[] memory=pair(field(data,"MemUsage")),network=pair(field(data,"NetIO")),disk=pair(field(data,"BlockIO"));if(memory[1]<=0||memory[0]>memory[1])return null;String pids=field(data,"PIDs");if(!pids.matches("[0-9]{1,7}"))return null;int count=Integer.parseInt(pids);if(count>1000000)return null;return new Sample(value.observedAt(),percent,memory[0],memory[1],network[0],network[1],disk[0],disk[1],count);
  }catch(Exception error){return null;}
 }
 private static String field(JsonNode data,String field){var value=data.path(field);if(!value.isTextual()||value.asText().length()>100)throw new IllegalArgumentException();return value.asText();}
 private static long[] pair(String value){String[] split=value.split("/",-1);if(split.length!=2)throw new IllegalArgumentException();return new long[]{bytes(split[0].strip()),bytes(split[1].strip())};}
 static long bytes(String value){var matcher=java.util.regex.Pattern.compile("([0-9]{1,15}(?:\\.[0-9]{1,9})?)\\s*(B|kB|KB|MB|GB|TB|KiB|MiB|GiB|TiB)").matcher(value);if(!matcher.matches())throw new IllegalArgumentException();String unit=matcher.group(2);int exponent=switch(unit){case "B"->0;case "kB","KB","KiB"->1;case "MB","MiB"->2;case "GB","GiB"->3;case "TB","TiB"->4;default->throw new IllegalArgumentException();};return new BigDecimal(matcher.group(1)).multiply(BigDecimal.valueOf(unit.contains("i")?1024:1000).pow(exponent)).setScale(0,RoundingMode.HALF_UP).longValueExact();}
}

@RestController @Validated
class MetricHistoryController {
 private final MetricHistory history;private final ServerAccess access;private final JdbcTemplate jdbc;
 MetricHistoryController(MetricHistory history,ServerAccess access,JdbcTemplate jdbc){this.history=history;this.access=access;this.jdbc=jdbc;}
 @GetMapping("/api/v1/servers/{serverId}/metrics/history")
 public MetricHistory.History get(@PathVariable String serverId,@RequestParam(defaultValue="24")@Min(1)@Max(24)int hours,Authentication auth){access.require(auth,serverId,false);if(jdbc.queryForObject("SELECT COUNT(*) FROM managed_server WHERE id=?",Integer.class,serverId)!=1)throw new de.elbracht.smp.api.ApiException(404,"SERVER_NOT_FOUND","Server nicht vorhanden.");return history.read(serverId,hours);}
}
