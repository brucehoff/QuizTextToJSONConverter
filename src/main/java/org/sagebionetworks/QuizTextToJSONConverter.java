package org.sagebionetworks;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.quiz.MultichoiceAnswer;
import org.sagebionetworks.repo.model.quiz.MultichoiceQuestion;
import org.sagebionetworks.repo.model.quiz.Question;
import org.sagebionetworks.repo.model.quiz.QuestionVariety;
import org.sagebionetworks.repo.model.quiz.QuizGenerator;
import org.sagebionetworks.schema.adapter.JSONObjectAdapter;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.JSONObjectAdapterImpl;

public class QuizTextToJSONConverter {
	private static final int NUM_QUESTIONS_PER_VARIETY = 3;
	
	private static final String WIKI_ENTITY_PREFIX = "https://www.synapse.org/#!Wiki:";
	private static final String WIKI_ID_PREFIX = "/ENTITY/";
	
	public static void main(String[] args) throws Exception {
		QuizGenerator gen = new QuizGenerator();
		gen.setId(1L);
		List<QuestionVariety> qvs = new ArrayList<QuestionVariety>();
		gen.setQuestions(qvs);
		
		InputStream is = QuizTextToJSONConverter.class.getClassLoader().getResourceAsStream("quiz.txt");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		boolean startQuestion = true;
		boolean startQuestionVariety = true;
		List<MultichoiceAnswer> answers = null;
		long questionIndex = 0;
		long responseIndex = 0;
		int lineCount= 0;
		//String questionVarietyHeader = null;
		String wikiEntityId = null;
		String wikiId = null;
		while (true) {
			String s = br.readLine();
			lineCount++;
			if (s==null) break;
			s = s.trim();
			if (lineCount==1) {
				// very first line
				gen.setHeader(s);
			} else if (startQuestionVariety) {
				if (s.length()==0) continue; // extra blank line
				wikiEntityId = null;
				wikiId = null;
				int i = s.indexOf(WIKI_ENTITY_PREFIX);
				if (i>=0) {
					int j = s.indexOf(WIKI_ID_PREFIX, i+WIKI_ENTITY_PREFIX.length());
					if (j>=0) {
						wikiEntityId = s.substring(i+WIKI_ENTITY_PREFIX.length(), j);
						wikiId = s.substring(j+WIKI_ID_PREFIX.length());
					}
				}
				QuestionVariety qv = new QuestionVariety();
				qvs.add(qv);
				List<Question> questionOptions = new ArrayList<Question>();
				qv.setQuestionOptions(questionOptions);
				startQuestionVariety=false;
			} else if (startQuestion) {
				if (s.length()==0) continue; // extra blank line
				MultichoiceQuestion q = new MultichoiceQuestion();
				q.setExclusive(true);
				q.setPrompt(s);
				WikiPageKey reference = new WikiPageKey();
				reference.setOwnerObjectType(ObjectType.ENTITY);
				reference.setOwnerObjectId(wikiEntityId);
				reference.setWikiPageId(wikiId);
				q.setReference(reference);
				q.setQuestionIndex(questionIndex++);
				answers = new ArrayList<MultichoiceAnswer>();
				q.setAnswers(answers);
				QuestionVariety qv = qvs.get(qvs.size()-1);
				List<Question> questionOptions = qv.getQuestionOptions();
				questionOptions.add(q);
				startQuestion=false;
				responseIndex = 0;
			} else if (s.length()==0){
				startQuestion=true;
				QuestionVariety qv = qvs.get(qvs.size()-1);
				if (qv.getQuestionOptions().size()>=NUM_QUESTIONS_PER_VARIETY) startQuestionVariety = true;
			} else {
				// it's a response
				boolean isCorrect = false;
				if (s.charAt(0)=='*') {
					isCorrect=true;
					s = s.substring(1);
				}
				MultichoiceAnswer answer = new MultichoiceAnswer();
				if (s.charAt(1)!=')') {
					char letter = (char)('a'+responseIndex);
					System.out.println("prepending "+letter+" to <"+s+">");
					s = letter+" "+s; // add 'a', 'b', etc. at front
				}
				answer.setPrompt(s); // the text already starts with a, b, c
				answer.setAnswerIndex(responseIndex);
				answer.setIsCorrect(isCorrect);
				//answer.setIsCorrect(isCorrect); // Don't know how to set this yet
				answers.add(answer);
				responseIndex++;
			}
		}
		gen.setMinimumScore((long)gen.getQuestions().size()-1);
		
		// some light validation
		if (gen.getQuestions().size()!=10) 
			throw new RuntimeException("Unexpected # of question varieties: "+gen.getQuestions().size());
		for (QuestionVariety var : gen.getQuestions()) {
			if (var.getQuestionOptions().size()!=3)
				throw new RuntimeException("Unexpected # of question options: "+
					var.getQuestionOptions().size());
			for  (Question q : var.getQuestionOptions()) {
				if (q.getPrompt().trim().length()==0) throw new RuntimeException("missing prompt");
				if (q instanceof MultichoiceQuestion) {
					MultichoiceQuestion mq = (MultichoiceQuestion)q;
					if (mq.getAnswers().size()<2) throw new RuntimeException(""+mq.getAnswers().size()+" answers for "+q.getPrompt());
					int correctCount = 0;
					for (MultichoiceAnswer a : mq.getAnswers()) {
						if (a.getIsCorrect()!=null && a.getIsCorrect()) correctCount++;
					}
					if (correctCount==0) throw new RuntimeException("No correct answer for "+q.getPrompt());
					mq.setExclusive(correctCount==1);
				}
			}
		}
		// now serialize the result
		JSONObjectAdapter adapter = new JSONObjectAdapterImpl();
		gen.writeToJSONObject(adapter);
		String quizGeneratorAsString = adapter.toJSONString();
		FileWriter writer = new FileWriter(new File("../repository-managers.certifiedUsersTestDefault.json"));
		writer.write(quizGeneratorAsString);
		writer.close();
		System.out.println(quizGeneratorAsString);
		// format the output: http://www.freeformatter.com/json-formatter.html
	}
	
}
