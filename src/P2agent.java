import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

import edu.cwru.sepia.action.*;
import edu.cwru.sepia.agent.Agent;
import edu.cwru.sepia.environment.model.history.ActionLogger;
import edu.cwru.sepia.environment.model.history.ActionResultLogger;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode.Type;
import edu.cwru.sepia.environment.model.state.ResourceType;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Template;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import org.omg.PortableInterceptor.ACTIVE;
import sun.awt.image.ImageWatched;

public class P2agent extends Agent {
	int foonumber = 10000;
	int timeInterval = 50;
	Map<OurActionType, Integer> estimated_time = new HashMap<OurActionType, Integer>();
	List<highLevelAction> total_plan;
	Map<Integer, List<Integer>> state_recorder;
	Map<Integer, List<Action>> action_recorder;
	List<Integer> time_value;
	Map<Integer, Integer> estimatedTimeCount = new HashMap<>();
	Map<Integer, Integer> peasantsProgress = new HashMap<>();
	public P2agent(int arg0) {
		super(arg0);
		// TODO Auto-generated constructor stub

	}

	@Override
	public void loadPlayerData(InputStream arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<Integer, Action> middleStep(StateView newstate, HistoryView arg1) {
		// TODO Auto-generated method stub
//		G=[peasant, townhall, farm, gold, wood]
		int[] G = new int[]{0, 0, 0, 10000, 10000};
		int projectedGoldNumber;
		int projectedWoodNumber;


		scheduleOutput bestPlan;
		scheduleOutput scheduledPlan;

		int current_time = newstate.getTurnNumber();
		Map<Integer, Action> actions = new HashMap<Integer, Action>();
		List<Integer> myUnitIds = newstate.getUnitIds(playernum);
		List<Integer> peasantIds = new ArrayList<Integer>();
		List<Integer> townhallIds = new ArrayList<Integer>();
		List<Integer> farmIds = new ArrayList<>();
		int availableFood = newstate.getSupplyCap(playernum)-newstate.getSupplyAmount(playernum);
		for(Integer unitID : myUnitIds)
				{
				UnitView unit = newstate.getUnit(unitID);
		   String unitTypeName = unit.getTemplateView().getName();
		   if(unitTypeName.equals("TownHall"))
				   townhallIds.add(unitID);
		   else if(unitTypeName.equals("Peasant"))
				   peasantIds.add(unitID);
		   else if(unitTypeName.equals("Farm"))
		   	       farmIds.add(unitID);
			}

		int currentGold = newstate.getResourceAmount(playernum, ResourceType.GOLD);
		int currentWood = newstate.getResourceAmount(playernum, ResourceType.WOOD);

		projectedGoldNumber = currentGold;
		projectedWoodNumber = currentWood;

		List<Integer> peasant_free_index = new LinkedList<Integer>();
		List<Integer> townhall_free_index = new LinkedList<Integer>();
		Map<Integer, ActionResult>  foo = arg1.getPrimitiveFeedback(playernum, (current_time-1)<0?0:(current_time-1));
		//Main Loop
		//Check if there is any available actions
		//1: available peasant
		for (Integer pid :peasantIds){
			if (newstate.getUnit(pid).getCargoAmount() > 0 && newstate.getUnit(pid).getCurrentDurativeAction() == null){
				if (!estimatedTimeCount.containsKey(pid)){
					continue;
				}
				int nearestTownhallId = calculateNearestUnit(newstate, newstate.getUnit(pid), townhallIds);
				actions.put(pid, new TargetedAction(pid, ActionType.COMPOUNDDEPOSIT, nearestTownhallId));
				if (newstate.getUnit(pid).getCargoType() == ResourceType.GOLD){
					projectedGoldNumber += newstate.getUnit(pid).getCargoAmount();
					int halfCollectGoldTime = current_time - estimatedTimeCount.get(pid)-200;
					estimatedTimeCount.remove(pid);
					int originalEstimatedTime = estimated_time.get(OurActionType.Collect_Gold);
					estimated_time.put(OurActionType.Collect_Gold, (halfCollectGoldTime*2+200+originalEstimatedTime)/2);
				}
				else if (newstate.getUnit(pid).getCargoType() == ResourceType.WOOD){
					projectedWoodNumber += newstate.getUnit(pid).getCargoAmount();
					int halfCollectWoodTime = current_time - estimatedTimeCount.get(pid)-1000;
					estimatedTimeCount.remove(pid);
					int originalEstimatedTime = estimated_time.get(OurActionType.Collect_Wood);
					estimated_time.put(OurActionType.Collect_Wood, (halfCollectWoodTime*2+1000+originalEstimatedTime)/2);
				}
			}
			else if (newstate.getUnit(pid).getCurrentDurativeAction() == null){
				peasant_free_index.add(pid);
			}
		}
		for (Integer pid :townhallIds){
			if (foo.containsKey(pid)) {
				if (foo.get(pid).getFeedback() == ActionFeedback.COMPLETED) {
					townhall_free_index.add(pid);
				}
			}
			else{
				townhall_free_index.add(pid);
			}
		}
		if (current_time%timeInterval == 0) {
			for (Integer pid:peasantIds){
				if (peasantsProgress.containsKey(pid)){
					if (newstate.getUnit(pid).getCurrentDurativeProgress()==peasantsProgress.get(pid)){
						peasant_free_index.add(pid);
					}
					else {
						peasantsProgress.put(pid, newstate.getUnit(pid).getCurrentDurativeProgress());
					}
				}
				else {
					peasantsProgress.put(pid, newstate.getUnit(pid).getCurrentDurativeProgress());
				}
			}
			int[] S = new int[]{peasantIds.size(), townhallIds.size(), farmIds.size(), projectedGoldNumber, projectedWoodNumber};
			List<highLevelAction> plan = MEA(S.clone(),G, availableFood);
			List<highLevelAction> ac = addTimeStamp(plan, current_time, timeInterval);
			bestPlan = schedule(ac, newstate, peasant_free_index, townhall_free_index,arg1, timeInterval);

			List<List<int[]>> Gn = new ArrayList<>();
			List<int[]> G1 = new ArrayList<>();
			G1.add(new int[]{2,0,0,0,0});
			Gn.add(G1);
			List<int[]> G2 = new ArrayList<>();
			G2.add(new int[]{3,0,0,0,0});
			Gn.add(G2);
			List<highLevelAction> singlePlan;
			for (int i=0;i<Gn.size();i++){
				singlePlan = MEA(S.clone(), Gn.get(i).get(0), availableFood);
				for (int j=1;j<Gn.get(i).size();j++) {
					singlePlan = concatenate(singlePlan, MEA(Gn.get(i).get(j-1).clone(), Gn.get(i).get(j), availableFood));
				}
				singlePlan = concatenate(singlePlan, MEA(Gn.get(i).get(Gn.get(i).size()-1).clone(), G, availableFood));
				singlePlan = addTimeStamp(singlePlan, current_time, timeInterval);

				scheduledPlan = schedule(singlePlan, newstate, peasant_free_index, townhall_free_index,arg1, timeInterval);
				if (scheduledPlan.makeSpan < bestPlan.makeSpan){
					bestPlan = scheduledPlan;
				}
			}
			for (Action singleAction:bestPlan.actions) {
				ActionType singleActionType = singleAction.getType();
				int singleActionUnitId = singleAction.getUnitId();
				actions.put(singleActionUnitId, singleAction);
				if (singleActionType==ActionType.COMPOUNDGATHER){
					estimatedTimeCount.put(singleActionUnitId, current_time);
				}
			}
		}
		return actions;
	}

	public scheduleOutput schedule(List<highLevelAction> total_plan, StateView newstate, List<Integer> peasantIds,
								   List<Integer> townhallIds, HistoryView h1, int time_interval) {
		//Find IDs of all units
		List<Integer> myUnitIds = newstate.getUnitIds(playernum);
		List<Integer> farmIds = new ArrayList<>();
		List<Integer> goldMines = newstate.getResourceNodeIds(Type.GOLD_MINE);
		List<Integer> trees = newstate.getResourceNodeIds(Type.TREE);
		Map<Integer, List<Action>> action_recorder = new HashMap<>();
		for(Integer unitID : myUnitIds) {
			UnitView unit = newstate.getUnit(unitID);
			String unitTypeName = unit.getTemplateView().getName();
			if(unitTypeName.equals("Farm"))
				farmIds.add(unitID);
		}
		//Calculate remaining food
		int food = newstate.getSupplyCap(playernum)-newstate.getSupplyAmount(playernum);


		int current_time = newstate.getTurnNumber();
		List<Integer> time_value= new LinkedList<>();
		//add current time to the first node of time value table
		for (int i =current_time;i<=total_plan.get(total_plan.size()-1).startTurn;i+=time_interval){
			time_value.add(i);
		}
		//state hash table
		Map<Integer, List<Integer>> state = new HashMap<>();
		//insert initial state
		for (int time :time_value){
			state.put(time,Arrays.asList(newstate.getResourceAmount(playernum, ResourceType.GOLD),newstate.getResourceAmount(playernum, ResourceType.WOOD),food));
		}
		//Peasant available hash table
		Map<Integer, List<Integer>> peasant_table = new HashMap<>();
		//Peasant available hash table
		Map<Integer, List<Integer>> townhall_table = new HashMap<>();
		//insert initial available peasants
		try {
			for (int time : time_value) {
				peasant_table.put(time, deepCopy(peasantIds));
			}
			//insert initial available townhalls
			for (int time : time_value) {
				townhall_table.put(time, deepCopy(townhallIds));
			}

			//initialize action recorder
			for (int time : time_value) {
				action_recorder.put(time, new LinkedList<>());
			}
		}catch (Exception e){
			System.out.println(e);
		}

		Iterator<highLevelAction> iter = total_plan.iterator();
		//loop through high level total order plan
		while(iter.hasNext()){
			//read information from this action
			highLevelAction current_action = iter.next();
			int current_action_start_time = current_action.startTurn;
			int current_action_end_time = current_action.endTurn;
			OurActionType type = current_action.action;
			if (type == OurActionType.Collect_Gold){
				for (int i = current_action_start_time;i >= current_time;i-= time_interval){
					List<Integer> current_peasant_index = peasant_table.get(i);
					if (current_peasant_index.size()>0 && goldMines.size()>0 && i!=current_time){
						//we move forward if this state satisfies preconditions.
					}
					else{
						//current state does not satisfy preconditions, we move to last time stamp that satisfies preconditions.
						int correct_time;
						if (i!=current_time) {
							correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
						}
						else {
							if (current_action_start_time==current_time){
								correct_time=current_time;
							}
							else {
								correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
							}
						}
						current_peasant_index = peasant_table.get(correct_time);
						if (current_peasant_index.size()==0){
							break;
						}
						//add the action to parallel list
						List<Action> parallel_actions_at_this_time = action_recorder.get(correct_time);
						parallel_actions_at_this_time.add(new TargetedAction(current_peasant_index.get(0), ActionType.COMPOUNDGATHER, goldMines.get(0)));
						action_recorder.put(correct_time,parallel_actions_at_this_time);
						//remove this peasant
						int removed_peasant = current_peasant_index.remove(0);
						int duration ;
						if  ((current_action_end_time-current_action_start_time)%estimated_time.get(type) ==0)
							duration = (current_action_end_time-current_action_start_time)/estimated_time.get(type);
						else
							duration= (current_action_end_time-current_action_start_time)/estimated_time.get(type) + 1;
						int end_time = correct_time+duration*time_interval;
						for (int j = correct_time+time_interval; j< end_time;j+= time_interval){
							List<Integer> temp_peasant_index = peasant_table.get(j);
							temp_peasant_index.remove((Integer)removed_peasant);
							peasant_table.put(j,temp_peasant_index);
						}
						//add gold at end of this action
						for (int j = end_time; j<= time_value.get(time_value.size()-1) ;j+= time_interval){
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0)+100,temp.get(1),temp.get(2)));
						}
						break;
					}
				}
			}

			if (type == OurActionType.Collect_Wood){
				for (int i = current_action_start_time;i >= current_time;i-= time_interval){
					List<Integer> current_peasant_index = peasant_table.get(i);
					if (current_peasant_index.size()>0 && trees.size()>0 && i!=current_time){
						//we move forward if this state satisfies preconditions.
					}
					else{
						//current state does not satisfy preconditions, we move to last time stamp that satisfies preconditions.
						int correct_time;
						if (i!=current_time) {
							correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
						}
						else {
							if (current_action_start_time==current_time){
								correct_time=current_time;
							}
							else {
								correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
							}
						}
						current_peasant_index = peasant_table.get(correct_time);
						if (current_peasant_index.size()==0){
							break;
						}
						//add the action to parallel list
						List<Action> parallel_actions_at_this_time = action_recorder.get(correct_time);
						parallel_actions_at_this_time.add(new TargetedAction(current_peasant_index.get(0), ActionType.COMPOUNDGATHER, trees.get(0)));
						action_recorder.put(correct_time,parallel_actions_at_this_time);
						//remove this peasant
						int removed_peasant = current_peasant_index.remove(0);
						int duration ;
						if  ((current_action_end_time-current_action_start_time)%estimated_time.get(type) ==0)
							duration = (current_action_end_time-current_action_start_time)/estimated_time.get(type);
						else
							duration= (current_action_end_time-current_action_start_time)/estimated_time.get(type) + 1;
						int end_time = correct_time+duration*time_interval;
						for (int j = correct_time; j< end_time;j+= time_interval){
							List<Integer> temp_peasant_index = peasant_table.get(j);
							temp_peasant_index.remove((Integer)removed_peasant);
							peasant_table.put(j,temp_peasant_index);
						}
						//add gold at end of this action
						for (int j = end_time; j<= time_value.get(time_value.size()-1) ;j+= time_interval){
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0),temp.get(1)+100,temp.get(2)));
						}
						break;
					}
				}
			}

			if (type == OurActionType.Build_Peasant){
				for (int i = current_action_start_time;i >= current_time;i-= time_interval){
					List<Integer> current_townhall_index = townhall_table.get(i);
					List<Integer> temp_state = state.get(i);
					if (temp_state.get(0)>=400&&townhall_table.get(i).size()>0&&temp_state.get(2)>0 && i!=current_time){
						//we move forward if this state satisfies preconditions.
					}
					else{
						//current state does not satisfy preconditions, we move to last time stamp that satisfies preconditions.
						int correct_time;
						if (i!=current_time) {
							correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
						}
						else {
							if (current_action_start_time==current_time){
								correct_time=current_time;
							}
							else {
								correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
							}
						}
						current_townhall_index = townhall_table.get(correct_time);
						if (current_townhall_index.size()==0){
							break;
						}
						//add the action to parallel list
						List<Action> parallel_actions_at_this_time = action_recorder.get(correct_time);
						int tvid = newstate.getTemplate(playernum,"Peasant").getID();
						parallel_actions_at_this_time.add(Action.createCompoundProduction(current_townhall_index.get(0),tvid));
						action_recorder.put(correct_time,parallel_actions_at_this_time);
						//remove this townhall
						int remove_townhall = current_townhall_index.remove(0);
						int duration ;
						if  ((current_action_end_time-current_action_start_time)%estimated_time.get(type) ==0)
							duration = (current_action_end_time-current_action_start_time)/estimated_time.get(type);
						else
							duration= (current_action_end_time-current_action_start_time)/estimated_time.get(type) + 1;
						int end_time = correct_time+duration*time_interval;

						for (int j = correct_time; j< end_time;j+= time_interval){
							List<Integer> temp_townhall_index = townhall_table.get(j);
							temp_townhall_index.remove((Integer)remove_townhall);
							townhall_table.put(j,temp_townhall_index);
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0)-400,temp.get(1),temp.get(2)-1));
						}
						//add gold at end of this action
						for (int j = end_time; j<= time_value.get(time_value.size()-1) ;j+= time_interval){
							List<Integer> temp = state.get(j);
							List<Integer> temp_peasant_index = peasant_table.get(j);
							temp_peasant_index.add(foonumber);
							peasant_table.put(j,temp_peasant_index);
							state.put(j,Arrays.asList(temp.get(0)-400,temp.get(1),temp.get(2)-1));
						}
						foonumber+=1;
						break;
					}
				}
			}

			if (type == OurActionType.Build_Farm){
				for (int i = current_action_start_time;i >= current_time;i-= time_interval){
					List<Integer> current_peasant_index = peasant_table.get(i);
					List<Integer> temp_state = state.get(i);
					if (temp_state.get(0)>=500&&temp_state.get(1)>=250&& current_peasant_index.size()>0 && i!=current_time){
						//we move forward if this state satisfies preconditions.
					}
					else{
						//current state does not satisfy preconditions, we move to last time stamp that satisfies preconditions.
						int correct_time;
						if (i!=current_time) {
							correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
						}
						else {
							if (current_action_start_time==current_time){
								correct_time=current_time;
							}
							else {
								correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
							}
						}
						current_peasant_index = peasant_table.get(correct_time);
						if (current_peasant_index.size()==0){
							break;
						}
						//add the action to parallel list
						List<Action> parallel_actions_at_this_time = action_recorder.get(correct_time);
						int tvid = newstate.getTemplate(playernum,"Farm").getID();
						parallel_actions_at_this_time.add(Action.createCompoundBuild(current_peasant_index.get(0),tvid,newstate.getClosestOpenPosition(10,10)[0],newstate.getClosestOpenPosition(10,10)[1]));
						action_recorder.put(correct_time,parallel_actions_at_this_time);
						//remove this peasant
						int removed_peasant = current_peasant_index.remove(0);
						int duration ;
						if  ((current_action_end_time-current_action_start_time)%estimated_time.get(type) ==0)
							duration = (current_action_end_time-current_action_start_time)/estimated_time.get(type);
						else
							duration= (current_action_end_time-current_action_start_time)/estimated_time.get(type) + 1;
						int end_time = correct_time+duration*time_interval;

						for (int j = correct_time; j< end_time;j+= time_interval){
							List<Integer> temp_peasant_index = peasant_table.get(j);
							temp_peasant_index.remove((Integer)removed_peasant);
							peasant_table.put(j,temp_peasant_index);
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0)-500,temp.get(1)-250,temp.get(2)));
						}
						//add gold at end of this action
						for (int j = end_time; j<= time_value.get(time_value.size()-1) ;j+= time_interval){
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0)-500,temp.get(1)-250,temp.get(2)+4));
						}
						break;
					}
				}
			}


			if (type == OurActionType.Build_Townhall){
				for (int i = current_action_start_time;i >= current_time;i-= time_interval){
					List<Integer> current_peasant_index = peasant_table.get(i);
					List<Integer> temp_state = state.get(i);
					if (temp_state.get(0)>=1200&&temp_state.get(1)>=800&&current_peasant_index.size()>0 && i!=current_time){
						//we move forward if this state satisfies preconditions.
					}
					else{
						//current state does not satisfy preconditions, we move to last time stamp that satisfies preconditions.
						int correct_time;
						if (i!=current_time) {
							correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
						}
						else {
							if (current_action_start_time==current_time){
								correct_time=current_time;
							}
							else {
								correct_time = (i + time_interval)>total_plan.get(total_plan.size()-1).startTurn?i:(i + time_interval);
							}
						}
						current_peasant_index = peasant_table.get(correct_time);
						if (current_peasant_index.size()==0){
							break;
						}
						//add the action to parallel list
						List<Action> parallel_actions_at_this_time = action_recorder.get(correct_time);
						int tvid = newstate.getTemplate(playernum,"TownHall").getID();
						parallel_actions_at_this_time.add(Action.createCompoundBuild(current_peasant_index.get(0),tvid,newstate.getClosestOpenPosition(10,10)[0],newstate.getClosestOpenPosition(10,10)[1]));
						action_recorder.put(correct_time,parallel_actions_at_this_time);
						//remove this peasant
						int removed_peasant = current_peasant_index.remove(0);
						int duration ;
						if  ((current_action_end_time-current_action_start_time)%estimated_time.get(type) ==0)
							duration = (current_action_end_time-current_action_start_time)/estimated_time.get(type);
						else
							duration= (current_action_end_time-current_action_start_time)/estimated_time.get(type) + 1;
						int end_time = correct_time+duration*time_interval;

						for (int j = correct_time; j< end_time;j+= time_interval){
							List<Integer> temp_peasant_index = peasant_table.get(j);
							temp_peasant_index.remove((Integer)removed_peasant);
							peasant_table.put(j,temp_peasant_index);
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0)-1200,temp.get(1)-800,temp.get(2)));
						}
						//add gold at end of this action
						for (int j = end_time; j<= time_value.get(time_value.size()-1) ;j+= time_interval){
							List<Integer> temp_townhall = townhall_table.get(j);
							temp_townhall.add(foonumber);
							townhall_table.put(j,temp_townhall);
							List<Integer> temp = state.get(j);
							state.put(j,Arrays.asList(temp.get(0)-1200,temp.get(1)-800,temp.get(2)+1));
						}
						foonumber += 1;
						break;
					}
				}
			}
		}

		int right_bound = 0;
		for (int i = time_value.size()-1;i>=0;i--){
			if (action_recorder.get(time_value.get(i)).size()>0){
				right_bound = time_value.get(i);
				break;
			}
		}
		return new scheduleOutput(action_recorder.get(current_time),right_bound-current_time);
	}





	@Override
	public void savePlayerData(OutputStream arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void terminalStep(StateView arg0, HistoryView arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public Map<Integer, Action> initialStep(StateView arg0, HistoryView arg1) {
		// TODO Auto-generated method stub
		estimated_time.put(OurActionType.Collect_Gold,400 );
		estimated_time.put(OurActionType.Collect_Wood,1000 );
		estimated_time.put(OurActionType.Build_Peasant,225 );
		estimated_time.put(OurActionType.Build_Townhall,1785 );
		estimated_time.put(OurActionType.Build_Farm,700 );
		return middleStep(arg0, arg1);
	}

//	G=[peasant, townhall, farm, gold, wood]
	public List<highLevelAction> MEA(int[] S, int[] G, int availableFood){
		assert S.length==G.length;
		for (int i=0;i<S.length;i++){
			if (S[i]<G[i]){
				OurActionType actionType;
				int[] g = new int[]{0,0,0,0,0};
				int k;
				if (i==0){
					actionType = OurActionType.Build_Peasant;
					k = (G[0]-S[0])>0?G[0]-S[0]:0;
					g[3] += (k*400-S[3])>0?(k*400):0;
					int numFarm = (int)Math.ceil((double)(k-availableFood)/4.0);
					if (numFarm>0) {
						g[2] += numFarm;
					}
				}
				else if (i==1){
					actionType = OurActionType.Build_Townhall;
					k = (G[1]-S[1])>0?G[1]-S[1]:0;
					g[3] += (k*1200-S[3])>0?(k*1200):0;
					g[4] += (k*800-S[4])>0?(k*800):0;
				}
				else if (i==2){
					actionType = OurActionType.Build_Farm;
					k = (G[2]-S[2])>0?G[2]-S[2]:0;
					g[3] += (k*500-S[3])>0?(k*500):0;
					g[4] += (k*250-S[4])>0?(k*250):0;
				}
				else if (i==3){
					actionType = OurActionType.Collect_Gold;
					k = (int)Math.ceil((float)(G[3]-S[3])/100.0);
					if (k<0)
						k=0;
				}
				else {
					actionType = OurActionType.Collect_Wood;
					k = (int)Math.ceil((float)(G[4]-S[4])/100.0);
					if (k<0)
						k=0;
				}
				List<highLevelAction> pre = MEA(S.clone(), g, availableFood);
				List<highLevelAction> acts = new ArrayList<>();
				for (int j=0;j<k;j++){
					acts.add(new highLevelAction(actionType, 0, 0));
				}
				List<highLevelAction> plan = concatenate(pre, acts);
				for (highLevelAction singleAction: plan){
					if (singleAction.action==OurActionType.Collect_Gold){
						S[3] += 100;
					}
					else if (singleAction.action==OurActionType.Collect_Wood){
						S[4] += 100;
					}
					else if (singleAction.action==OurActionType.Build_Peasant){
						S[0] += 1;
						S[3] -= 400;
						availableFood -= 1;
					}
					else if (singleAction.action==OurActionType.Build_Townhall){
						S[1] += 1;
						S[3] -= 1200;
						S[4] -= 800;
						availableFood += 1;
					}
					else if (singleAction.action==OurActionType.Build_Farm){
						S[2] += 1;
						S[3] -= 500;
						S[4] -= 250;
						availableFood += 4;
					}
				}
				return concatenate(plan, MEA(S.clone(), G, availableFood));
			}
		}
		return null;
	}


	public List<highLevelAction> addTimeStamp(List<highLevelAction> plan, int currentTurnNumber, int timeInterval){
		try {
			for (int i = 0; i < plan.size(); i++) {
				highLevelAction action = plan.get(i);
				action.startTurn = currentTurnNumber;
				int duration = estimated_time.get(action.action);
				action.endTurn = currentTurnNumber+duration;
				currentTurnNumber += (duration/timeInterval+((duration%timeInterval>0)?1:0))*timeInterval;
			}
		}catch(NullPointerException e){
			System.out.println("NO PLAN FOUND");
		}
		return plan;
	}


	public List<highLevelAction> concatenate(List<highLevelAction> list1, List<highLevelAction> list2){
		List<highLevelAction> contacList = new ArrayList<>();
		if (list1==null||list1.size()==0){
			return list2;
		}
		else if (list2==null||list2.size()==0){
			return list1;
		}
		else {
			contacList.addAll(list1);
			contacList.addAll(list2);
		}
		return contacList;
	}

	public int calculateNearestUnit(StateView S, UnitView unit, List<Integer> targetList){
		assert targetList.size() != 0;
		int unitX = unit.getXPosition();
		int unitY = unit.getYPosition();
		double minimumDistance = 1000000;
		int miniumTargetID = -1;
		for (Integer targetID: targetList){
			UnitView target = S.getUnit(targetID);
			int targetX = target.getXPosition();
			int targetY = target.getYPosition();
			double distance = Math.pow(unitX - targetX, 2) + Math.pow(unitY - targetY, 2);
			if (distance<minimumDistance){
				minimumDistance = distance;
				miniumTargetID = targetID;
			}
		}
		return miniumTargetID;
	}

	public static <T> List<T> deepCopy(List<T> src)throws IOException, ClassNotFoundException {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(byteOut);
		out.writeObject(src);
		ByteArrayInputStream byteIn = new ByteArrayInputStream(byteOut.toByteArray());
		ObjectInputStream in = new ObjectInputStream(byteIn);
		@SuppressWarnings("unchecked")
		List<T> dest = (List<T>) in.readObject();
		return dest;
	}
}
