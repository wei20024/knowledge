package org.support.project.knowledge.bat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.support.project.common.config.INT_FLAG;
import org.support.project.common.config.LocaleConfigLoader;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.StringUtils;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.config.MailConfig;
import org.support.project.knowledge.config.SystemConfig;
import org.support.project.knowledge.dao.CommentsDao;
import org.support.project.knowledge.dao.ExUsersDao;
import org.support.project.knowledge.dao.KnowledgesDao;
import org.support.project.knowledge.dao.LikesDao;
import org.support.project.knowledge.dao.NotifyConfigsDao;
import org.support.project.knowledge.dao.NotifyQueuesDao;
import org.support.project.knowledge.dao.TargetsDao;
import org.support.project.knowledge.entity.CommentsEntity;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.LikesEntity;
import org.support.project.knowledge.entity.NotifyConfigsEntity;
import org.support.project.knowledge.entity.NotifyQueuesEntity;
import org.support.project.knowledge.logic.KnowledgeLogic;
import org.support.project.knowledge.vo.GroupUser;
import org.support.project.knowledge.vo.Notify;
import org.support.project.web.dao.MailsDao;
import org.support.project.web.dao.SystemConfigsDao;
import org.support.project.web.dao.UsersDao;
import org.support.project.web.entity.GroupsEntity;
import org.support.project.web.entity.MailsEntity;
import org.support.project.web.entity.SystemConfigsEntity;
import org.support.project.web.entity.UsersEntity;

public class NotifyMailBat extends AbstractBat {
	/** ログ */
	private static Log LOG = LogFactory.getLog(MailSendBat.class);
	
	private static final String MAIL_CONFIG_DIR = "/org/support/project/knowledge/mail/";
	private static final DateFormat DAY_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

	public static void main(String[] args) throws Exception {
		AppConfig.initEnvKey("KNOWLEDGE_HOME");
		
		NotifyMailBat bat = new NotifyMailBat();
		bat.dbInit();
		bat.start();
	}
	
	/**
	 * 通知キューを処理して、メール送信テーブルにメール通知を登録する
	 */
	private void start() {
		NotifyQueuesDao notifyQueuesDao = NotifyQueuesDao.get();
		List<NotifyQueuesEntity> notifyQueuesEntities = notifyQueuesDao.selectAll();
		for (NotifyQueuesEntity notifyQueuesEntity : notifyQueuesEntities) {
			if (notifyQueuesEntity.getType() == Notify.TYPE_KNOWLEDGE_INSERT 
					|| notifyQueuesEntity.getType() == Notify.TYPE_KNOWLEDGE_UPDATE) {
				notifyKnowledgeUpdate(notifyQueuesEntity);
			} else if (notifyQueuesEntity.getType() == Notify.TYPE_KNOWLEDGE_COMMENT) {
				notifyCommentInsert(notifyQueuesEntity);
			} else if (notifyQueuesEntity.getType() == Notify.TYPE_KNOWLEDGE_LIKE) {
				notifyLikeInsert(notifyQueuesEntity);
			}
			// 通知のキューから削除
			notifyQueuesDao.delete(notifyQueuesEntity);
			//notifyQueuesDao.physicalDelete(notifyQueuesEntity);
		}
	}
	
	/**
	 * 指定のナレッジにアクセスするURLを作成
	 * @param knowledge
	 * @return
	 */
	private String makeURL(KnowledgesEntity knowledge) {
		SystemConfigsDao dao = SystemConfigsDao.get();
		SystemConfigsEntity config = dao.selectOnKey(SystemConfig.SYSTEM_URL, AppConfig.SYSTEM_NAME);
		if (config == null) {
			return "";
		}
		
		StringBuilder builder = new StringBuilder();
		builder.append(config.getConfigValue());
		if (!config.getConfigValue().endsWith("/")) {
			builder.append("/");
		}
		builder.append("protect.knowledge/view/");
		builder.append(knowledge.getKnowledgeId());
		return builder.toString();
	}
	
	
	/**
	 * イイネが押された
	 * @param notifyQueuesEntity
	 */
	private void notifyLikeInsert(NotifyQueuesEntity notifyQueuesEntity) {
		LikesDao likesDao = LikesDao.get();
		LikesEntity like = likesDao.selectOnKey(notifyQueuesEntity.getId());
		KnowledgesDao knowledgesDao = KnowledgesDao.get();
		KnowledgesEntity knowledge = knowledgesDao.selectOnKey(like.getKnowledgeId());
		
		UsersDao usersDao = UsersDao.get();
		UsersEntity likeUser = usersDao.selectOnKey(like.getInsertUser());
		
		// 登録者に通知
		UsersEntity user = usersDao.selectOnKey(knowledge.getInsertUser());
		if (user != null) {
			NotifyConfigsDao notifyConfigsDao = NotifyConfigsDao.get();
			NotifyConfigsEntity notifyConfigsEntity = notifyConfigsDao.selectOnKey(user.getUserId());
			if (notifyConfigsEntity != null && INT_FLAG.flagCheck(notifyConfigsEntity.getMyItemLike())) {
				// 登録者でかつイイネが登録した場合に通知が欲しい
				Locale locale = user.getLocale();
				MailConfig config = LocaleConfigLoader.load(MAIL_CONFIG_DIR, "notify_insert_like_myitem", locale, MailConfig.class);;
				sendLikeMail(like, knowledge, likeUser, user, config);
			}
		}
	}

	private void sendLikeMail(LikesEntity like, KnowledgesEntity knowledge, UsersEntity likeUser, UsersEntity user, MailConfig config) {
		MailsEntity mailsEntity = new MailsEntity();
		String mailId = idGenu("Notify");
		mailsEntity.setMailId(mailId);
		mailsEntity.setStatus(MailSendBat.MAIL_STATUS_UNSENT);
		mailsEntity.setToAddress(user.getUserKey());
		mailsEntity.setToName(user.getUserName());
		
		String title = config.getTitle();
		title = title.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
		title = title.replace("{KnowledgeTitle}", StringUtils.abbreviate(knowledge.getTitle(), 80));
		mailsEntity.setTitle(title);
		String contents = config.getContents();
		contents = contents.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
		contents = contents.replace("{KnowledgeTitle}", knowledge.getTitle());
		contents = contents.replace("{Contents}", knowledge.getContent());
		if (likeUser != null) {
			contents = contents.replace("{LikeInsertUser}", likeUser.getUserName());
		} else {
			contents = contents.replace("{LikeInsertUser}", "");
		}
		contents = contents.replace("{URL}", makeURL(knowledge));
		
		mailsEntity.setContent(contents);
		MailsDao.get().insert(mailsEntity);
	}

	/**
	 * ナレッジにコメントが登録された場合の通知
	 * @param notifyQueuesEntity
	 */
	private void notifyCommentInsert(NotifyQueuesEntity notifyQueuesEntity) {
		CommentsDao commentsDao = CommentsDao.get();
		CommentsEntity comment = commentsDao.selectOnKey(notifyQueuesEntity.getId());
		KnowledgesDao knowledgesDao = KnowledgesDao.get();
		KnowledgesEntity knowledge = knowledgesDao.selectOnKey(comment.getKnowledgeId());
		
		UsersDao usersDao = UsersDao.get();
		UsersEntity commentUser = usersDao.selectOnKey(comment.getInsertUser());
		
		// 登録者に通知
		UsersEntity user = usersDao.selectOnKey(knowledge.getInsertUser());
		if (user != null) {
			NotifyConfigsDao notifyConfigsDao = NotifyConfigsDao.get();
			NotifyConfigsEntity notifyConfigsEntity = notifyConfigsDao.selectOnKey(user.getUserId());
			if (notifyConfigsEntity != null && INT_FLAG.flagCheck(notifyConfigsEntity.getMyItemComment())) {
				// 登録者でかつコメントが登録した場合に通知が欲しい
				Locale locale = user.getLocale();
				MailConfig config = LocaleConfigLoader.load(MAIL_CONFIG_DIR, "notify_insert_comment_myitem", locale, MailConfig.class);;
				sendCommentMail(comment, knowledge, commentUser, user, config);
			}
		}
		
		// 宛先のナレッジにコメント追加で通知が欲しいユーザに通知
		List<UsersEntity> users = new ArrayList<>();
		// 宛先の一覧取得
		TargetsDao targetsDao = TargetsDao.get();
		List<UsersEntity> targetUsers = targetsDao.selectUsersOnKnowledgeId(knowledge.getKnowledgeId());
		users.addAll(targetUsers);
		
		//グループの一覧
		List<GroupsEntity> targetGroups = targetsDao.selectGroupsOnKnowledgeId(knowledge.getKnowledgeId());
		for (GroupsEntity groupsEntity : targetGroups) {
			List<GroupUser> groupUsers = ExUsersDao.get().selectGroupUser(groupsEntity.getGroupId(), 0, Integer.MAX_VALUE);
			for (GroupUser groupUser : groupUsers) {
				if (!contains(users, groupUser)) {
					users.add(groupUser);
				}
			}
		}
		Iterator<UsersEntity> iterator = users.iterator();
		while (iterator.hasNext()) {
			UsersEntity usersEntity = (UsersEntity) iterator.next();
			// 自分宛てのナレッジ登録／更新で通知するかどうかの判定
			NotifyConfigsDao notifyConfigsDao = NotifyConfigsDao.get();
			NotifyConfigsEntity notifyConfigsEntity = notifyConfigsDao.selectOnKey(usersEntity.getUserId());
			if (notifyConfigsEntity == null) {
				iterator.remove();
			} else if (!INT_FLAG.flagCheck(notifyConfigsEntity.getToItemComment())) {
				iterator.remove();
			} else if (user.getUserId().intValue() == knowledge.getInsertUser().intValue()) {
				// 登録者には通知しない（登録者に通知する／しないは、上のロジックで処理済)
				iterator.remove();
			}
		}
		
		for (UsersEntity target : users) {
			// 宛先にメール送信
			Locale locale = target.getLocale();
			MailConfig config = LocaleConfigLoader.load(MAIL_CONFIG_DIR, "notify_insert_comment", locale, MailConfig.class);;
			sendCommentMail(comment, knowledge, commentUser, target, config);
		}
	}
	
	
	/**
	 * コメントが追加されたメールを通知する
	 * @param comment コメントの情報
	 * @param knowledge ナレッジの情報
	 * @param commentUser コメントを登録したユーザの情報
	 * @param user メールの送信先
	 */
	private void sendCommentMail(CommentsEntity comment, KnowledgesEntity knowledge, UsersEntity commentUser, UsersEntity user, MailConfig config) {
		MailsEntity mailsEntity = new MailsEntity();
		String mailId = idGenu("Notify");
		mailsEntity.setMailId(mailId);
		mailsEntity.setStatus(MailSendBat.MAIL_STATUS_UNSENT);
		mailsEntity.setToAddress(user.getUserKey());
		mailsEntity.setToName(user.getUserName());
		
		String title = config.getTitle();
		title = title.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
		title = title.replace("{KnowledgeTitle}", StringUtils.abbreviate(knowledge.getTitle(), 80));
		mailsEntity.setTitle(title);
		String contents = config.getContents();
		contents = contents.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
		contents = contents.replace("{KnowledgeTitle}", knowledge.getTitle());
		contents = contents.replace("{Contents}", knowledge.getContent());
		if (commentUser != null) {
			contents = contents.replace("{CommentInsertUser}", commentUser.getUserName());
		} else {
			contents = contents.replace("{CommentInsertUser}", "");
		}
		contents = contents.replace("{CommentContents}", comment.getComment());
		contents = contents.replace("{URL}", makeURL(knowledge));

		mailsEntity.setContent(contents);
		MailsDao.get().insert(mailsEntity);
	}

	/**
	 * ナレッジを登録・更新した際にメール通知を送信する
	 * @param notifyQueuesEntity
	 */
	private void notifyKnowledgeUpdate(NotifyQueuesEntity notifyQueuesEntity) {
		// ナレッジが登録/更新された
		KnowledgesDao knowledgesDao = KnowledgesDao.get();
		KnowledgesEntity knowledge = knowledgesDao.selectOnKey(notifyQueuesEntity.getId());
		if (knowledge.getPublicFlag() == KnowledgeLogic.PUBLIC_FLAG_PUBLIC) {
			notifyPublicKnowledgeUpdate(notifyQueuesEntity, knowledge);
		} else if (knowledge.getPublicFlag() == KnowledgeLogic.PUBLIC_FLAG_PROTECT) {
			notifyProtectKnowledgeUpdate(notifyQueuesEntity, knowledge);
		}
		// 「非公開」のナレッジは、通知対象外
	}
	
	/**
	 * 「保護」のナレッジを登録・更新した際にメール通知を送信する
	 * @param notifyQueuesEntity
	 * @param knowledge
	 */
	private void notifyProtectKnowledgeUpdate(NotifyQueuesEntity notifyQueuesEntity, KnowledgesEntity knowledge) {
		List<UsersEntity> users = new ArrayList<>();
		// 宛先の一覧取得
		TargetsDao targetsDao = TargetsDao.get();
		List<UsersEntity> targetUsers = targetsDao.selectUsersOnKnowledgeId(knowledge.getKnowledgeId());
		users.addAll(targetUsers);
		
		//グループの一覧
		List<GroupsEntity> targetGroups = targetsDao.selectGroupsOnKnowledgeId(knowledge.getKnowledgeId());
		for (GroupsEntity groupsEntity : targetGroups) {
			List<GroupUser> groupUsers = ExUsersDao.get().selectGroupUser(groupsEntity.getGroupId(), 0, Integer.MAX_VALUE);
			for (GroupUser groupUser : groupUsers) {
				if (!contains(users, groupUser)) {
					users.add(groupUser);
				}
			}
		}
		
		Iterator<UsersEntity> iterator = users.iterator();
		while (iterator.hasNext()) {
			UsersEntity usersEntity = (UsersEntity) iterator.next();
			// 自分宛てのナレッジ登録／更新で通知するかどうかの判定
			NotifyConfigsDao notifyConfigsDao = NotifyConfigsDao.get();
			NotifyConfigsEntity notifyConfigsEntity = notifyConfigsDao.selectOnKey(usersEntity.getUserId());
			if (notifyConfigsEntity == null) {
				iterator.remove();
			} else if (!INT_FLAG.flagCheck(notifyConfigsEntity.getToItemSave())) {
				iterator.remove();
			}
		}
		notifyKnowledgeUpdateToUsers(notifyQueuesEntity, knowledge, users);
	}
	
	/**
	 * 既に指定のユーザが追加されているのか確認
	 * @param users
	 * @param groupUser
	 * @return
	 */
	private boolean contains(List<UsersEntity> users, GroupUser groupUser) {
		for (UsersEntity usersEntity : users) {
			if (usersEntity.equalsOnKey(groupUser)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 「公開」のナレッジを登録・更新した際にメール通知を送信する
	 * @param notifyQueuesEntity
	 * @param knowledge
	 */
	private void notifyPublicKnowledgeUpdate(NotifyQueuesEntity notifyQueuesEntity, KnowledgesEntity knowledge) {
		//ナレッジ登録ONでかつ、公開区分「公開」を除外しないユーザに通知
		List<UsersEntity> users = ExUsersDao.get().selectNotifyPublicUsers();
		notifyKnowledgeUpdateToUsers(notifyQueuesEntity, knowledge, users);
	}
	
	/**
	 * 指定のユーザ一覧に、ナレッジを登録・更新した際にメール通知を送信する
	 * @param notifyQueuesEntity
	 * @param knowledge
	 * @param users
	 */
	private void notifyKnowledgeUpdateToUsers(NotifyQueuesEntity notifyQueuesEntity, KnowledgesEntity knowledge, List<UsersEntity> users) {
		for (UsersEntity usersEntity : users) {
			if (LOG.isTraceEnabled()) {
				LOG.trace("[Notify] " + usersEntity.getUserKey());
			}
			Locale locale = usersEntity.getLocale();
			MailConfig config = null;
			if (notifyQueuesEntity.getType() == Notify.TYPE_KNOWLEDGE_INSERT) {
				config = LocaleConfigLoader.load(MAIL_CONFIG_DIR, "notify_insert_knowledge", locale, MailConfig.class);
			} else {
				config = LocaleConfigLoader.load(MAIL_CONFIG_DIR, "notify_update_knowledge", locale, MailConfig.class);
			}
			
			insertNotifyKnowledgeUpdateMailQue(knowledge, usersEntity, config);
		}
	}
	
	
	/**
	 * メール送信のキュー情報を登録する
	 * @param knowledge
	 * @param usersEntity
	 * @param config
	 */
	private void insertNotifyKnowledgeUpdateMailQue(KnowledgesEntity knowledge, UsersEntity usersEntity, MailConfig config) {
		MailsEntity mailsEntity = new MailsEntity();
		String mailId = idGenu("Notify");
		mailsEntity.setMailId(mailId);
		mailsEntity.setStatus(MailSendBat.MAIL_STATUS_UNSENT);
		mailsEntity.setToAddress(usersEntity.getUserKey());
		mailsEntity.setToName(usersEntity.getUserName());
		String title = config.getTitle();
		title = title.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
		title = title.replace("{KnowledgeTitle}", StringUtils.abbreviate(knowledge.getTitle(), 80));
		mailsEntity.setTitle(title);
		String contents = config.getContents();
		contents = contents.replace("{KnowledgeId}", knowledge.getKnowledgeId().toString());
		contents = contents.replace("{KnowledgeTitle}", knowledge.getTitle());
		contents = contents.replace("{Contents}", knowledge.getContent());
		contents = contents.replace("{URL}", makeURL(knowledge));

		mailsEntity.setContent(contents);
		MailsDao.get().insert(mailsEntity);
	}
	
	
	/**
	 * メール送信のIDを生成
	 * @param string
	 * @return
	 */
	private String idGenu(String label) {
		StringBuilder builder = new StringBuilder();
		builder.append(label);
		builder.append("-");
		builder.append(DAY_FORMAT.format(new Date()));
		builder.append("-");
		builder.append(UUID.randomUUID().toString());
		return builder.toString();
	}


}
