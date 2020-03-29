/*
 * Copyright (c) 2020, Wild Adventure
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 4. Redistribution of this software in source or binary forms shall be free
 *    of all charges or fees to the recipient of this software.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package wild.api.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.SQLException;
import java.util.logging.Level;

import lombok.Getter;
import lombok.NonNull;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import wild.api.chat.Chat;

/**
 * Wrapper for the default command executor.
 */
public abstract class CommandFramework extends Command {
	
	@Getter private final Plugin plugin;
	protected String originalLabel;
	protected String permission;
	protected BaseComponent[] noPermissionMessage;
	private boolean asyncSQL;
	private BaseComponent[] asyncSQLMessage;
	
	public CommandFramework(@NonNull Plugin plugin, @NonNull String label, String... aliases) {
		super(label, null, aliases);
		this.plugin = plugin;
		this.originalLabel = label;
		
		Permission permission = getClass().getAnnotation(Permission.class);
		if (permission != null) {
			this.permission = permission.value();
		}
		
		NoPermissionMessage noPermMsg = getClass().getAnnotation(NoPermissionMessage.class);
		if (noPermMsg != null) {
			this.noPermissionMessage = TextComponent.fromLegacyText(ChatColor.RED + noPermMsg.value());
		} else {
			this.noPermissionMessage = Chat.makeArray("Non hai il permesso per usare questo comando.", ChatColor.RED);
		}
		
		AsyncSQL asyncSQL = getClass().getAnnotation(AsyncSQL.class);
		if (asyncSQL != null) {
			this.asyncSQL = true;
			this.asyncSQLMessage = TextComponent.fromLegacyText(ChatColor.RED + asyncSQL.value());
		}
	}
	
	
	
	@Override
	public final void execute(final CommandSender sender, final String[] args) {
		
		if (permission != null && !sender.hasPermission(permission)) {
			sender.sendMessage(noPermissionMessage);
			return;
		}
		
		if (asyncSQL) {
			
			ProxyServer.getInstance().getScheduler().runAsync(getPlugin(), new Runnable() {
				
				@Override
				public void run() {
					tryOnCommand(sender, args);
				}
			});
			
		} else {
			tryOnCommand(sender, args);
		}
	}
	
	private void tryOnCommand(CommandSender sender, String[] args) {
		try {
			onCommand(sender, args);
			
		} catch (ExecuteException e) {
			// Totally fine
			if (e.getMessage() != null) {
				sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + e.getMessage()));
			}
		} catch (Throwable t) {
			
			if (asyncSQL && t instanceof SQLException) {
				if (asyncSQLMessage != null) {
					sender.sendMessage(asyncSQLMessage);
				}
			} else {
				Chat.tell(sender, "Si è verificato un errore interno durante l'esecuzione del comando.", ChatColor.RED);
			}
			
			getPlugin().getLogger().log(Level.SEVERE, "Error while " + sender.getName() + " was executing /" + getName() + ":", t);
		}
	}

	public abstract void onCommand(CommandSender sender, String[] args);

		
	public static class ExecuteException extends RuntimeException {

		private static final long serialVersionUID = 7052164163215272979L;
		
		public ExecuteException(String msg) {
			super(msg);
		}
		
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface Permission {

		String value();
		
	}
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface NoPermissionMessage {

		String value();

	}
	
	
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public static @interface AsyncSQL {

		String value();
		
	}

	
	public static class CommandValidate {

		public static void notNull(Object o, String msg) {
			if (o == null) {
				throw new ExecuteException(msg);
			}
		}
		
		public static void isTrue(boolean b, String msg) {
			if (!b) {
				throw new ExecuteException(msg);
			}
		}
		
		public static ProxiedPlayer getPlayerSender(CommandSender sender) {
			if (sender instanceof ProxiedPlayer) {
				return (ProxiedPlayer) sender;
			} else {
				throw new ExecuteException("Non puoi farlo dalla console.");
			}
		}
		
		public static double getDouble(String input) {
			try {
				return Double.parseDouble(input);
			} catch (NumberFormatException e) {
				throw new ExecuteException("Numero non valido.");
			}
		}
		
		public static int getInteger(String input) {
			try {
				int i = Integer.parseInt(input);
				return i;
			} catch (NumberFormatException e) {
				throw new ExecuteException("Numero non valido.");
			}
		}
		
		public static int getPositiveInteger(String input) {
			try {
				int i = Integer.parseInt(input);
				if (i < 0) {
					throw new ExecuteException("Devi inserire un numero positivo.");
				}
				return i;
			} catch (NumberFormatException e) {
				throw new ExecuteException("Numero non valido.");
			}
		}
		
		public static int getPositiveIntegerNotZero(String input) {
			try {
				int i = Integer.parseInt(input);
				if (i <= 0) {
					throw new ExecuteException("Devi inserire un numero positivo.");
				}
				return i;
			} catch (NumberFormatException e) {
				throw new ExecuteException("Numero non valido.");
			}
		}
		
		public static void minLength(Object[] array, int minLength, String msg) {
			if (array.length < minLength) {
				throw new ExecuteException(msg);
			}
		}
	}
}
