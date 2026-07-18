package com.questkeeper.quest;

import com.questkeeper.quest.model.Quest;
import java.util.*;

public final class QuestManager implements com.questkeeper.quest.service.QuestProgressService.QuestManagerView {
    private Map<String, Quest> quests = Map.of();
    public void replace(Map<String, Quest> values) { quests = Map.copyOf(values); }
    public Quest get(String id) { return quests.get(id); }
    public Collection<Quest> all() { return quests.values(); }
    public boolean contains(String id) { return quests.containsKey(id); }
}
