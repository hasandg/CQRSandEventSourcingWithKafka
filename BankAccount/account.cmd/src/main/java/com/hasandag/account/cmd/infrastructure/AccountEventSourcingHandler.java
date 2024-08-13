package com.hasandag.account.cmd.infrastructure;

import com.hasandag.account.cmd.domain.AccountAggregate;
import com.hasandag.cqrs.core.domain.AggregateRoot;
import com.hasandag.cqrs.core.events.BaseEvent;
import com.hasandag.cqrs.core.hadlers.EventSourcingHandler;
import com.hasandag.cqrs.core.infrastructure.EventStore;
import com.hasandag.cqrs.core.producers.EventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
public class AccountEventSourcingHandler implements EventSourcingHandler<AccountAggregate> {

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventProducer eventProducer;

    @Value("${spring.kafka.topic}")
    private String topic;

    @Override
    public void save(AggregateRoot aggregate) {
        eventStore.saveEvents(aggregate.getId(), aggregate.getUncommittedChanges(), aggregate.getVersion());
        aggregate.markChangesAsCommitted();
    }

    @Override
    public AccountAggregate getById(String id) {
        var aggregate = new AccountAggregate();
        var events = eventStore.getEvents(id);
        if(events != null && !events.isEmpty()) {
            aggregate. replayEvents(events);
            var latestVersion = events.stream().map(BaseEvent::getVersion).max(Comparator.naturalOrder());
            aggregate.setVersion(latestVersion.get());
        }
        return aggregate;
    }

    @Override
    public void republishEvents() {
        var aggregateIds = eventStore.getAggregateIds();
        for(var aggregateId : aggregateIds) {
            var aggregate = getById(aggregateId);
            if(aggregate == null || !aggregate.isActive()) continue;
            var events = eventStore.getEvents(aggregateId);
            for (var event : events) {
                eventProducer.produce(topic, event);
            }
        }
    }
}
